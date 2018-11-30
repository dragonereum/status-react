(ns status-im.notifications.core
  (:require [goog.object :as object]
            [re-frame.core :as re-frame]
            [status-im.react-native.js-dependencies :as rn]
            [taoensso.timbre :as log]
            [status-im.i18n :as i18n]
            [status-im.accounts.db :as accounts.db]
            [status-im.chat.models :as chat-model]
            [status-im.utils.platform :as platform]
            [status-im.utils.fx :as fx]))

;; Work in progress namespace responsible for push notifications and interacting
;; with Firebase Cloud Messaging.

(when-not platform/desktop?

  (def firebase (object/get rn/react-native-firebase "default")))

;; NOTE: Only need to explicitly request permissions on iOS.
(defn request-permissions []
  (if platform/desktop?
    (re-frame/dispatch [:notifications.callback/request-notifications-permissions-granted {}])
    (-> (.requestPermission (.messaging firebase))
        (.then
         (fn [_]
           (log/debug "notifications-granted")
           (re-frame/dispatch [:notifications.callback/request-notifications-permissions-granted {}]))
         (fn [_]
           (log/debug "notifications-denied")
           (re-frame/dispatch [:notifications.callback/request-notifications-permissions-denied {}]))))))

(defn valid-notification-payload?
  [{:keys [from to] :as payload}]
  (and from to))

(defn create-notification-payload
  [{:keys [from to] :as payload}]
  (if (valid-notification-payload? payload)
    {:msg (js/JSON.stringify #js {:from from
                                  :to   to})}
    (throw (str "Invalid push notification payload" payload))))

(when platform/desktop?
  (defn handle-initial-push-notification [] ())) ;; no-op

(when-not platform/desktop?

  (def channel-id "status-im")
  (def channel-name "Status")
  (def sound-name "message.wav")
  (def group-id "im.status.ethereum.MESSAGE")
  (def icon "ic_stat_status_notification")

  (defn get-notification-payload [message-js]
    ;; message-js.-data is Notification.data():
    ;; https://github.com/invertase/react-native-firebase/blob/adcbeac3d11585dd63922ef178ff6fd886d5aa9b/src/modules/notifications/Notification.js#L79
    (let [data    (.. message-js -data)
          msg     (js/JSON.parse (object/get data "msg"))
          from    (object/get msg "from")
          to      (object/get msg "to")
          payload {:from from
                   :to   to}]
      (if (valid-notification-payload? payload)
        payload
        (log/warn "failed to retrieve notification payload from" (js/JSON.stringify data)))))

  (defn display-notification [{:keys [title body from to]}]
    (let [notification (firebase.notifications.Notification.)]
      (.. notification
          (setTitle title)
          (setBody body)
          (setData (clj->js (create-notification-payload {:from from
                                                          :to to})))
          (setSound sound-name))
      (when platform/android?
        (.. notification
            (-android.setChannelId channel-id)
            (-android.setAutoCancel true)
            (-android.setPriority firebase.notifications.Android.Priority.High)
            (-android.setGroup group-id)
            (-android.setGroupSummary true)
            (-android.setSmallIcon icon)))
      (.. firebase
          notifications
          (displayNotification notification)
          (then #(log/debug "Display Notification" title body))
          (catch (fn [error]
                   (log/debug "Display Notification error" title body error))))))

  (defn get-fcm-token []
    (-> (.getToken (.messaging firebase))
        (.then (fn [x]
                 (log/debug "get-fcm-token:" x)
                 (re-frame/dispatch [:notifications.callback/get-fcm-token-success x])))))

  (defn on-refresh-fcm-token []
    (.onTokenRefresh (.messaging firebase)
                     (fn [x]
                       (log/debug "on-refresh-fcm-token:" x)
                       (re-frame/dispatch [:notifications.callback/get-fcm-token-success x]))))

  (defn on-notification []
    (.onMessage (.messaging firebase)
                (fn [message-js]
                  (log/debug "onMessage called")
                  (let [payload (get-notification-payload message-js)]
                    (when payload
                      (display-notification (merge {:title (i18n/label :notifications-new-message-title)
                                                    :body  (i18n/label :notifications-new-message-body)}
                                                   payload)))))))

  (defn create-notification-channel []
    (let [channel (firebase.notifications.Android.Channel. channel-id
                                                           channel-name
                                                           firebase.notifications.Android.Importance.High)]
      (.setSound channel sound-name)
      (.setShowBadge channel true)
      (.enableVibration channel true)
      (.. firebase
          notifications
          -android
          (createChannel channel)
          (then #(log/debug "Notification channel created:" channel-id)
                #(log/error "Notification channel creation error:" channel-id %)))))

  (fx/defn handle-push-notification
    [{:keys [db] :as cofx} {:keys [from to] :as event}]
    (let [current-public-key (accounts.db/current-public-key cofx)]
      (if (= to current-public-key)
        (fx/merge cofx
                  {:db (update db :push-notifications/stored dissoc to)}
                  (chat-model/navigate-to-chat from nil))
        {:db (assoc-in db [:push-notifications/stored to] from)})))

  (defn handle-notification-event [event] ;; https://github.com/invertase/react-native-firebase/blob/adcbeac3d11585dd63922ef178ff6fd886d5aa9b/src/modules/notifications/Notification.js#L13
    (let [payload (get-notification-payload (.. event -notification))]
      (when payload
        (re-frame/dispatch [:notifications/notification-event-received payload]))))

  (defn handle-initial-push-notification
    "This method handles pending push notifications. It is only needed to handle PNs from legacy clients (which use firebase.notifications API)"
    []
    (.. firebase
        notifications
        getInitialNotification
        (then (fn [event]
                (when event
                  (handle-notification-event event))))))

  (defn on-notification-opened []
    (.. firebase
        notifications
        (onNotificationOpened handle-notification-event)))

  (defn init []
    (on-refresh-fcm-token)
    (on-notification)
    (on-notification-opened)
    (when platform/android?
      (create-notification-channel))))

(fx/defn process-stored-event [cofx address]
  (when-not platform/desktop?
    (let [to (get-in cofx [:db :accounts/accounts address :public-key])
          from (get-in cofx [:db :push-notifications/stored to])]
      (when from
        (handle-push-notification cofx
                                  {:from from
                                   :to   to})))))

(re-frame/reg-fx
 :notifications/display-notification
 display-notification)

(re-frame/reg-fx
 :notifications/handle-initial-push-notification
 handle-initial-push-notification)

(re-frame/reg-fx
 :notifications/get-fcm-token
 (fn [_]
   (when platform/mobile?
     (get-fcm-token))))

(re-frame/reg-fx
 :notifications/request-notifications-permissions
 (fn [_]
   (request-permissions)))
