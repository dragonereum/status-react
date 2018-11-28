(ns status-im.ui.components.popup-menu.views
  (:require [status-im.ui.components.popup-menu.styles :as styles]
            [status-im.ui.components.react :as react]
            [status-im.react-native.js-dependencies :as rn-dependencies]
            [status-im.utils.platform :as platform]
            [taoensso.timbre :as log]))

(defn wrap-with-menu [component items left-click-action right-click-action]
  (if platform/desktop?
    (let [left-click-action (if (= left-click-action :menu) :menu
                                left-click-action)
          right-click-action (if (= right-click-action :menu) :menu
                                 right-click-action)]
      [react/touchable-highlight
       {:on-press (fn [arg]
                    (let [right-click? (= "right" (.-button (.-nativeEvent arg)))
                          action (if right-click? right-click-action left-click-action)]
                      (log/debug "### wrap-with-menu" right-click?)
                      (if (= :menu action)
                        (.show rn-dependencies/desktop-menu
                               (clj->js (mapv #(hash-map :text (:text %1) :onPress (:on-select %1)) items)))
                        (action))))}
       component])

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
        component]])))

