(ns
  ^{:doc "Resource management support for HTML5 WebSocket connections."
    :author "Frank Mosebach"}
  fm.websockets.resources
  (:require
    [fm.resources.store :as rsc-store]
    [fm.resources.slot-extensions :as rsc-slot-ext]))

(defn with-resource-store [connection resource-store]
  (assert connection)
  (assert resource-store)
  (assoc connection ::resource-store resource-store))

(defn resource-store [connection]
  (assert connection)
  (if-let [resource-store (::resource-store connection)]
    resource-store
    (throw (IllegalStateException.
             "Connection does not have a resource store!"))))

(def ^{:private true} valid-scopes #{:request :connection :application})

(def ^{:private true} ordered-scopes [:request :connection :application])

(defn- valid-scope? [scope]
  (contains? valid-scopes scope))

(defn- with-scope [slots scope]
  (rsc-slot-ext/with-scope slots scope ordered-scopes))

(defn store! [connection key resource scope & {:as kwargs}]
  (assert connection)
  (assert resource)
  (assert (valid-scope? scope))
  (let [store  (resource-store connection)
        kwargs (update-in kwargs [:slots] with-scope scope)]
    (apply rsc-store/store! store key resource (flatten (seq kwargs)))))

(defn send! [connection signal & args]
  (assert connection)
  (apply rsc-store/send! (resource-store connection) signal args))

(defn send-to! [connection keys signal & args]
  (assert connection)
  (apply rsc-store/send-to! (resource-store connection) keys signal args))

(defn remove! [connection & keys]
  (assert connection)
  (apply rsc-store/remove! (resource-store connection) keys))

(defn get-resource
  ([connection key]
    (get-resource connection key nil))
  ([connection key default]
    (assert connection)
    (rsc-store/get-resource (resource-store connection) key default)))

(defn request-expired! [connection]
  (assert connection)
  (rsc-slot-ext/scope-expired! (resource-store connection) :request))

(defn connection-expired! [connection]
  (assert connection)
  (rsc-slot-ext/scope-expired! (resource-store connection) :connection))

(defn application-expired! [store]
  (assert store)
  (rsc-slot-ext/scope-expired! store :application))

(defn request-handler [request-handler]
  (assert request-handler)
  (fn [connection method params]
    (try
      (request-handler connection method params)
      (finally
        (request-expired! connection)))))

(defn connection-handler [connection-handler store-constructor]
  (assert connection-handler)
  (assert store-constructor)
  (fn [connection]
    (let [store      (store-constructor connection)
          connection (with-resource-store connection store)]
      (try
        (connection-handler connection)
        (finally
          (connection-expired! connection))))))          

