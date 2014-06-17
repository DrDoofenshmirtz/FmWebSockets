(ns
  ^{:doc 
  
  "Resource management support for HTML5 WebSockets connections."
    
    :author "Frank Mosebach"}
  fm.websockets.resources
  (:require
    [fm.resources.store :as rsc-store]
    [fm.resources.slot-extensions :as slxt])
  (:import 
    (fm.websockets.exceptions ConnectionClosed)))

(defn resource-store [connection context]
  (assert connection)
  (assert context)
  (if-let [resource-stores (::resource-stores connection)]
    (if-let [resource-store (resource-stores context)]
      resource-store
      (throw (IllegalStateException. 
               (format "No resource store found for context '%s'!" context))))
    (throw (IllegalStateException.
             "Connection does not have any attached resource stores!"))))

(def ^{:private true} ordered-scopes [:request 
                                      :message 
                                      :connection 
                                      :application])

(def ^{:private true} valid-scopes (set ordered-scopes))

(def ^{:private true} hook-signals [:before-request    :after-request 
                                    :before-message    :after-message
                                    :before-connection :after-connection])

(defn- valid-scope? [scope]
  (contains? valid-scopes scope))

(defn- with-expiration-slots [kwargs scope]
  (update-in kwargs [:slots] #(slxt/with-scope % scope ordered-scopes)))

(defn- with-hook-slots [kwargs]
  (let [hook-slots (select-keys kwargs hook-signals)]
    (if-not (empty? hook-slots)
      (apply dissoc 
             (update-in kwargs [:slots] merge hook-slots) 
             hook-signals)
      kwargs)))

(defn- with-scope-slots [kwargs scope]
  (-> kwargs (with-expiration-slots scope) with-hook-slots))

(defn store! [connection context key resource scope & {:as kwargs}]
  (assert connection)
  (assert context)
  (assert resource)
  (assert (valid-scope? scope))
  (let [resource-store (resource-store connection context)
        kwargs         (-> kwargs (with-scope-slots scope) seq flatten)]
    (apply rsc-store/store! resource-store key resource kwargs)))

(defn- update-stores! [connection update]
  (assert connection)
  (reduce (fn [results [context resource-store]]
            (if-let [resources (update resource-store)]
              (assoc results context resources)
              results)) 
          nil 
          (::resource-stores connection)))

(defn send! [connection signal & args]
  (update-stores! connection #(apply rsc-store/send! % signal args)))

(defn send-to! [connection keys signal & args]
  (update-stores! connection #(apply rsc-store/send-to! % keys signal args)))

(defn remove! [connection & keys]
  (update-stores! connection #(apply rsc-store/remove! % keys)))

(defn- ensure-connected [resource-store]
  (when-not (true? (rsc-store/get-resource resource-store ::connected))
    (throw (ConnectionClosed. "Resource lookup failed: connection closed!"))))

(defn get-resource
  ([connection context key]
    (get-resource connection context key nil))
  ([connection context key default]
    (assert connection)
    (assert context)
    (let [resource-store (resource-store connection context)
          resource       (rsc-store/get-resource resource-store key default)]
      (ensure-connected resource-store)
      resource)))

(defn request-expired! [connection]
  (update-stores! connection #(slxt/scope-expired! % :request)))

(defn message-expired! [connection]
  (update-stores! connection #(slxt/scope-expired! % :message)))

(defn connection-expired! [connection]
  (update-stores! connection #(slxt/scope-expired! % :connection)))

(defn application-expired! [store]
  (assert store)
  (slxt/scope-expired! store :application))

(defn- with-prefix [keywrd prefix]
  (keyword (str prefix \- (name keywrd))))

(defn- call-hooks [position scope connection args]
  (let [signal (with-prefix scope position)]
    (update-stores! connection #(apply rsc-store/send! % signal args))))

(defn- call-before-hooks [scope connection args]
  (call-hooks "before" scope connection args))

(defn- call-after-hooks [scope connection args]
  (call-hooks "after" scope connection args))

(defn- with-scope-hooks [handler scope]
  (assert (not (nil? handler)))
  (assert (valid-scope? scope))
  (fn [connection & args]
    (let [hook-args (cons connection args)]
      (try
        (call-before-hooks scope connection hook-args)
        (let [result (apply handler connection args)]
          (call-after-hooks scope connection hook-args)
          result)        
        (finally
          (update-stores! connection #(slxt/scope-expired! % scope)))))))

(defn request-handler [request-handler]
  (with-scope-hooks request-handler :request))

(defn message-handler [message-handler]
  (with-scope-hooks message-handler :message))

(defn with-resource-store [connection context resource-store]
  (assert connection)
  (assert context)
  (assert resource-store)
  (let [connection (assoc-in connection 
                             [::resource-stores context] 
                             resource-store)]
    (store! connection context ::connected true :connection)
    connection))

(defn- attach-resource-stores [connection store-provider contexts]
  (reduce #(with-resource-store connection % (store-provider connection %)) 
          connection 
          contexts))

(defn connection-handler [connection-handler store-provider context & contexts]
  (assert connection-handler)
  (assert store-provider)
  (assert context)
  (let [connection-handler (with-scope-hooks connection-handler :connection)
        contexts           (cons context contexts)]
    (fn [connection]
      (-> connection
          (attach-resource-stores store-provider contexts)
          connection-handler))))

