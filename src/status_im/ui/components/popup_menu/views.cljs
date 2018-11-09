(ns status-im.ui.components.popup-menu.views
  (:require [status-im.ui.components.popup-menu.styles :as styles]
            [status-im.ui.components.react :as react]
            [taoensso.timbre :as log]))

(defn wrap-with-menu [component items left-click-action right-click-action]
  (let [menu-ref (atom nil)
        left-click-action (if (= left-click-action :menu) #(.open @menu-ref)
                              (or left-click-action :none))
        right-click-action (if (= right-click-action :menu) #(.open @menu-ref)
                               (or right-click-action :none))]
    [react/popup-menu {:renderer (:NotAnimatedContextMenu react/popup-menu-renderers)
                       :ref #(reset! menu-ref %)}
     [react/popup-menu-trigger {:disabled true}]
     (into [react/popup-menu-options {:custom-styles {:options-wrapper styles/menu-style}}]
           (for [i items]
             [react/popup-menu-option i]))
     [react/touchable-highlight
      {:on-press #(let [right-click? (= "right" (.-button (.-nativeEvent %)))]
                    (log/debug "### wrap-with-menu" right-click?)
                    (if right-click? (right-click-action) (left-click-action)))}
      component]]))

