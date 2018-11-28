(ns status-im.ui.components.popup-menu.views
  (:require [status-im.ui.components.popup-menu.styles :as styles]
            [status-im.ui.components.react :as react]
            [status-im.react-native.js-dependencies :as rn-dependencies]
            [status-im.utils.platform :as platform]
            [status-im.i18n :as i18n]
            [re-frame.core :as re-frame]
            [taoensso.timbre :as log]))

(defn show-desktop-menu [items]
  (.show rn-dependencies/desktop-menu
         (clj->js (mapv #(hash-map :text (:text %1) :onPress (:on-select %1)) items))))

(defn wrap-with-menu [component items left-click-action right-click-action]
  (let [menu-ref (atom nil)
        left-click-action (if (= left-click-action :menu) #(.open @menu-ref)
                              left-click-action)
        right-click-action (if (= right-click-action :menu) #(.open @menu-ref)
                               right-click-action)]
    [react/popup-menu {:renderer (:NotAnimatedContextMenu react/popup-menu-renderers)
                       :ref #(reset! menu-ref %)}
     [react/popup-menu-trigger {:disabled true}]
     (into [react/popup-menu-options {:custom-styles {:options-wrapper styles/menu-style}}]
           (for [i items]
             [react/popup-menu-option i]))
     [react/touchable-highlight
      {:on-press #(let [right-click? (= "right" (.-button (.-nativeEvent %)))
                        action (if right-click? right-click-action left-click-action)]
                    (log/debug "### wrap-with-menu" right-click?)
                    (action))}
      component]]))

(defn get-chat-menu-items [public-key group-chat public? chat-id]
  (->> [(when (and (not group-chat) (not public?))
          {:text (i18n/label :t/view-profile)
           :on-select #(re-frame/dispatch [:show-profile-desktop public-key])})
        (when (and group-chat (not public?))
          {:text (i18n/label :t/group-info)
           :on-select #(re-frame/dispatch [:show-group-chat-profile])})
        {:text (i18n/label :t/clear-history)
         :on-select #(re-frame/dispatch [:chat.ui/clear-history-pressed])}
        {:text (i18n/label :t/delete-chat)
         :on-select #(re-frame/dispatch [(if (and group-chat (not public?))
                                           :group-chats.ui/remove-chat-pressed
                                           :chat.ui/remove-chat-pressed)
                                         chat-id])}]
       (remove nil?)))

