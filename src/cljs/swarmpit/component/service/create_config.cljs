(ns swarmpit.component.service.create-config
  (:require [material.icon :as icon]
            [material.component :as comp]
            [swarmpit.url :refer [dispatch!]]
            [swarmpit.component.mixin :as mixin]
            [swarmpit.component.handler :as handler]
            [swarmpit.component.state :as state]
            [swarmpit.component.service.form-settings :as settings]
            [swarmpit.component.service.form-ports :as ports]
            [swarmpit.component.service.form-networks :as networks]
            [swarmpit.component.service.form-mounts :as mounts]
            [swarmpit.component.service.form-secrets :as secrets]
            [swarmpit.component.service.form-variables :as variables]
            [swarmpit.component.service.form-deployment :as deployment]
            [swarmpit.component.message :as message]
            [swarmpit.routes :as routes]
            [rum.core :as rum]))

(enable-console-print!)

(defonce step-index (atom 0))

(def steps ["General settings" "Ports" "Networks" "Mounts" "Secrets" "Environment variables" "Deployment"])

(def step-style
  {:backgroundColor "transparent"})

(def step-content-style
  {:minWidth "600px"})

(def stepper-style
  {:height "60px"})

(defn- step-item [index form]
  (comp/step
    {:key (str "step-" index)}
    (comp/step-button
      {:key                (str "step-btn-" index)
       :disableTouchRipple true
       :style              step-style
       :className          "step-label"
       :onClick            (fn [] (reset! step-index index))}
      (nth steps index))
    (comp/step-content
      {:key   (str "step-context-" index)
       :style step-content-style}
      form)))

(defn- create-service-handler
  []
  (let [settings (state/get-value settings/cursor)
        ports (state/get-value ports/cursor)
        networks (state/get-value networks/cursor)
        mounts (state/get-value mounts/cursor)
        secrets (state/get-value secrets/cursor)
        variables (state/get-value variables/cursor)
        deployment (state/get-value deployment/cursor)]
    (handler/post
      (routes/path-for-backend :service-create)
      (-> settings
          (assoc :ports ports)
          (assoc :networks networks)
          (assoc :mounts mounts)
          (assoc :secrets secrets)
          (assoc :variables variables)
          (assoc :deployment deployment))
      (fn [response]
        (dispatch!
          (routes/path-for-frontend :service-info {:id (:ID response)}))
        (state/set-value {:text (str "Service " (:ID response) " has been created.")
                          :type :info
                          :open true} message/cursor))
      (fn [response]
        (state/set-value {:text (str "Service creation failed. Reason: " (:error response))
                          :type :error
                          :open true} message/cursor)))))

(defn init-state
  [registry repository-user repository]
  (reset! step-index 0)
  (if (= "dockerhub" registry)
    (settings/dockerhub-image-tags-handler repository-user repository)
    (settings/registry-image-tags-handler registry repository))
  (state/set-value {:repository  {:registry registry
                                  :user     repository-user
                                  :name     repository
                                  :tag      ""
                                  :tags     []}
                    :serviceName ""
                    :mode        "replicated"
                    :replicas    1
                    :isValid     false} settings/cursor)
  (state/set-value [] ports/cursor)
  (state/set-value [] networks/cursor)
  (state/set-value [] mounts/cursor)
  (state/set-value [] secrets/cursor)
  (state/set-value [] variables/cursor)
  (state/set-value {:autoredeploy  false
                    :parallelism   1
                    :delay         0
                    :failureAction "pause"} deployment/cursor))

(def init-state-mixin
  (mixin/init
    (fn [data]
      (init-state (:registry data)
                  (:repositoryUser data)
                  (:repository data)))))

(rum/defc form < rum/reactive
                 init-state-mixin [data]
  (let [index (rum/react step-index)
        {:keys [secrets volumes networks]} data
        {:keys [isValid]} (state/react settings/cursor)]
    [:div
     [:div.form-panel
      [:div.form-panel-left
       (comp/panel-info icon/services "New service")]
      [:div.form-panel-right
       (comp/mui
         (comp/raised-button
           {:label      "Create"
            :disabled   (not isValid)
            :primary    true
            :onTouchTap create-service-handler}))]]
     (comp/mui
       (comp/stepper
         {:activeStep  index
          :linear      false
          :style       stepper-style
          :orientation "vertical"}
         (step-item 0 (settings/form false))
         (step-item 1 (ports/form-create))
         (step-item 2 (networks/form-create networks))
         (step-item 3 (mounts/form-create volumes))
         (step-item 4 (secrets/form-create secrets))
         (step-item 5 (variables/form-create))
         (step-item 6 (deployment/form))))]))