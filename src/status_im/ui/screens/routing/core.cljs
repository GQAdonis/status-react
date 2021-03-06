(ns status-im.ui.screens.routing.core
  (:require
   [status-im.ui.components.react :as react]
   [status-im.ui.components.styles :as common-styles]
   [status-im.utils.navigation :as navigation]
   [cljs-react-navigation.reagent :as nav-reagent]
   [re-frame.core :as re-frame]
   [taoensso.timbre :as log]
   [status-im.utils.platform :as platform]
   [status-im.utils.core :as utils]
   [status-im.ui.screens.routing.screens :as screens]
   [status-im.ui.screens.routing.intro-login-stack :as intro-login-stack]
   [status-im.ui.screens.routing.chat-stack :as chat-stack]
   [status-im.ui.screens.routing.wallet-stack :as wallet-stack]
   [status-im.ui.screens.routing.profile-stack :as profile-stack]))

(defn wrap [view-id component]
  "Wraps screen with main view and adds navigation-events component"
  (fn []
    (let [main-view (react/create-main-screen-view view-id)]
      [main-view common-styles/flex
       [component]
       [:> navigation/navigation-events
        {:on-will-focus
         (fn []
           (log/debug :on-will-focus view-id)
           (re-frame/dispatch [:screens/on-will-focus view-id]))}]])))

(defn wrap-modal [modal-view component]
  "Wraps modal screen with necessary styling and adds :on-request-close handler
  on Android"
  (fn []
    (if platform/android?
      [react/view common-styles/modal
       [react/modal
        {:transparent      true
         :animation-type   :slide
         :on-request-close (fn []
                             (cond
                               (#{:wallet-send-transaction-modal
                                  :wallet-sign-message-modal}
                                modal-view)
                               (re-frame/dispatch
                                [:wallet/discard-transaction-navigate-back])

                               :else
                               (re-frame/dispatch [:navigate-back])))}
        [react/main-screen-modal-view modal-view
         [component]]]]
      [react/main-screen-modal-view modal-view
       [component]])))

(defn prepare-config [config]
  (-> config
      (utils/update-if-present :initialRouteName name)
      (utils/update-if-present :mode name)))

(defn stack-navigator [routes config]
  (nav-reagent/stack-navigator
   routes
   (merge {:headerMode "none"} (prepare-config config))))

(defn switch-navigator [routes config]
  (nav-reagent/switch-navigator
   routes
   (prepare-config config)))

(declare stack-screens)

(defn build-screen [screen]
  "Builds screen from specified configuration. Currently screen can be
  - keyword, which points to some specific route
  - vector of [:modal :screen-key] type when screen should be wrapped as modal
  - map with `name`, `screens`, `config` keys, where `screens` is a vector
    of children and `config` is `stack-navigator` configuration"
  (let [[screen-name screen-config]
        (cond (keyword? screen)
              [screen (screens/get-screen screen)]
              (map? screen)
              [(:name screen) screen]
              :else screen)]
    (let [res (cond
                (map? screen-config)
                (let [{:keys [screens config]} screen-config]
                  (stack-navigator
                   (stack-screens screens)
                   config))

                (vector? screen-config)
                (let [[_ screen] screen-config]
                  (nav-reagent/stack-screen
                   (wrap-modal screen-name screen)))

                :else
                (nav-reagent/stack-screen (wrap screen-name screen-config)))]
      [screen-name {:screen res}])))

(defn stack-screens [screens-map]
  (->> screens-map
       (map build-screen)
       (into {})))

(defn get-main-component [view-id]
  (log/debug :component view-id)
  (switch-navigator
   (->> [(intro-login-stack/intro-login-stack view-id)
         chat-stack/chat-stack
         wallet-stack/wallet-stack
         profile-stack/profile-stack]
        (map build-screen)
        (into {}))
   {:initialRouteName :intro-login-stack}))
