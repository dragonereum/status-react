(ns status-im.notifications.background
  (:require [goog.object :as object]
            [re-frame.core :as re-frame]
            [status-im.react-native.js-dependencies :as rn]
            [status-im.notifications.core :as notifications]
            [status-im.i18n :as i18n]
            [cljs.core.async :as async]
            [taoensso.timbre :as log]
            [status-im.utils.platform :as platform]))

(when-not platform/desktop?
  (def firebase (object/get rn/react-native-firebase "default")))

(defn message-handler-fn []
  ;; message-js is firebase.messaging.RemoteMessage: https://github.com/invertase/react-native-firebase-docs/blob/master/docs/messaging/reference/RemoteMessage.md
  (fn [message-js]
    (js/Promise.
     (fn [on-success on-error]
       (try
         (do (when message-js
               (log/debug "message-handler-fn called" (js/JSON.stringify message-js))
               (let [payload (notifications/get-notification-payload message-js)]
                 (when payload
                   (log/debug "calling display-notification to display background message" payload)
                   (notifications/display-notification (merge {:title (i18n/label :notifications-new-message-title)
                                                               :body  (i18n/label :notifications-new-message-body)}
                                                              payload)))))
             (on-success))
         (catch :default e
           (do (log/warn "failed to handle background message" e)
               (on-error e))))))))
