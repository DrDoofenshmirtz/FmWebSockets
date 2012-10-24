(ns
  ^{:doc "Operations for resource (lifecycle) management."
    :author "Frank Mosebach"}
  fm.websockets.resources.operations
  (:refer-clojure :exclude [remove]))

(defn manage [{:keys [good expired] :or {expired []} :as resources}
              key resource &
              {:keys [on-event expired? close!]
               :or   {on-event (fn [id event resource] resource)
                      expired? (constantly false)
                      close!   (constantly nil)}}]
  (assert resource)
  (let [replaced (get good key)
        good     (assoc good key {:resource resource
                                  :on-event on-event
                                  :expired? expired?
                                  :close!   close!})
        expired  (if replaced (conj expired [key replaced]) expired)]
    (assoc resources :good good :expired expired)))

(defn- expired? [[key {:keys [resource expired?]}]]
  (expired? resource))

(defn- update-entries [{:keys [good expired] :as resources} kees update]
  (if update
    (let [kees    (or (seq kees) (keys good))
          entries (map update (select-keys good kees))
          good    (into good (filter (complement expired?) entries))
          expired (into expired (filter expired? entries))]
      (assoc resources :good good :expired expired))
    resources))

(defn- update-resource [[key {resource :resource :as managed}] update args]
  (if update
    [key (assoc managed :resource (apply update resource args))]
    managed))

(defn update [{:keys [good expired] :as resources} &
              {:keys [keys update args]}]
  (assert update)
  (update-entries resources keys #(update-resource % update args)))

(defn- process-event [[key {:keys [resource on-event] :as managed}] id event]
  [key (assoc managed :resource (on-event resource))])

(defn send-event [{:keys [good expired] :as resources} &
                  {:keys [keys id event]}]
  (assert id)
  (assert event)
  (update-entries resources keys #(process-event % id event)))

(defn remove
  ([resources]
    (remove resources nil))
  ([{:keys [good expired] :as resources} keys]
    (let [good    (apply dissoc good keys)
          expired (into expired (select-keys good keys))]
      (assoc resources :good good :expired expired))))

(defn clean-up! [{expired :expired :as resources}]
  (io!
    (doseq [[key {:keys [resource close!]}] expired]
      (close! resource))
    (assoc resources :expired (empty expired))))
