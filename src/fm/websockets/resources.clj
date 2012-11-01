(ns
  ^{:doc "Resource management support for WebSocket connections."
    :author "Frank Mosebach"}
  fm.websockets.resources
  (:use
    [fm.websockets.resources.operations :only (with-default-functions)]
    [fm.websockets.resources.storage :only (manage! send!)]))

(defn with-resource-storage [connection storage]
  (assoc connection ::storage storage))

(defn resource-storage [connection]
  (if-let [storage (::storage connection)]
    storage
    (throw (IllegalStateException.
             "Connection does not have a resource storage!"))))

(def ^{:private true} levels-by-scope {:request     0
                                       :connection  1
                                       :application 2})

(defn- scope-level [scope]
  (get levels-by-scope scope Integer/MAX_VALUE))

(defn- expired? [scoped-resource expired-scope]
  (<= (scope-level (::scope scoped-resource)) (scope-level expired-scope)))

(defn- decorate-on-event [on-event]
  (fn [id event scoped-resource]
    (if (= id ::scope-expired)
      (let [resource-scope (::scope scoped-resource)
            expired-scope  (:scope event)
            resource       (on-event :scope-expired
                                     event
                                     (::resource scoped-resource))]
        (if (expired? scoped-resource expired-scope)
          (assoc scoped-resource ::resource resource ::expired? true)
          (assoc scoped-resource ::resource resource)))
      (let [resource (on-event id event (::resource scoped-resource))]
        (assoc scoped-resource ::resource resource)))))

(defn- decorate-close! [close!]
  (fn [scoped-resource]
    (close! (::resource scoped-resource))))

(defn- decorate-expired? [expired?]
  (fn [scoped-resource]
    (or (::expired? scoped-resource)
        (expired? (::resource scoped-resource)))))

(defn- scoped-resource-funcs [funcs]
  (let [{:keys [on-event expired? close!]} (with-default-functions funcs)]
    (assoc funcs :on-event (decorate-on-event on-event)
                 :expired? (decorate-expired? expired?)
                 :close!   (decorate-close! close!))))

(defn- scoped-resource [resource scope]
  {::resource resource ::scope (or scope :application)})

(def ^{:private true} scopes #{:request :connection :application})

(defn manage-resource [connection key resource scope & {:as funcs}]
  (assert connection)
  (assert resource)
  (assert (scopes scope))
  (let [storage  (resource-storage connection)
        resource (scoped-resource resource scope)
        funcs    (scoped-resource-funcs funcs)
        funcs    (interleave (keys funcs) (vals funcs))]
    (apply manage! storage key resource funcs)))

(defn request-expired [connection method params]
  (assert connection)
  (assert method)
  (send! (resource-storage connection)
         ::scope-expired
         {:scope      :request
          :connection connection
          :method     method
          :params     params}))

(defn connection-expired [connection]
  (assert connection)
  (send! (resource-storage connection)
         ::scope-expired
         {:scope      :connection
          :connection connection}))

(defn application-expired [storage]
  (assert storage)
  (send! storage ::scope-expired {:scope :application}))
