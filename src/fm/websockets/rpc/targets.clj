(ns
  ^{:doc 
  
  "Support for the convenient definition of targets for remote procedure calls."
    
    :author "Frank Mosebach"}
  fm.websockets.rpc.targets
  (:refer-clojure :exclude [read])
  (:require
    [fm.core.hyphenate :as hy]
    [fm.resources.core :as rsc]
    [fm.websockets.resources :as wsr]
    [fm.websockets.rpc.request :as req])
  (:import
    (java.util UUID)))

(defn- gen-action [name body]
  (if (= '=> (first body))
    (second body)
    `(fn ~name ~@body)))

(defmacro defaction [name & more]
  (let [[[doc-string? attributes?] more] (split-with #(not (or (= '=> %) 
                                                               (sequential? %))) 
                                                     more)
        var-meta    (if (string? doc-string?)
                      (assoc attributes? :doc doc-string?)
                      attributes?)
        target-meta {::target {::name `'~name ::type ::action}}]    
   `(def ~(vary-meta name merge var-meta target-meta)
          (vary-meta ~(gen-action name more) merge ~target-meta))))

(defn- wrap-resource [resource close!]
  {::resource resource
   ::close!   close!})

(defn- update-resource [wrapped-resource update]
  (-> wrapped-resource update rsc/good))

(defn- close-resource! [{resource ::resource close! ::close!}]
  (close! resource))

(def ^{:private true} resource-slots {::update update-resource})

(defn- channel-id []
  (str (UUID/randomUUID)))

(defn- channel-id? [id]
  (-> id str .trim .length (> 0)))

(defn- check-channel-id [id]
  (if-not (channel-id? id)
    (throw (IllegalArgumentException. (format "Illegal channel id: '%s'!" id))))
  id)

(defn- get-resource [connection channel-id]
  (if-let [resource (-> connection 
                        (wsr/get-resource (check-channel-id channel-id)) 
                        ::resource)]
    resource
    (throw (IllegalStateException. "Channel closed or aborted!"))))

(defn- open [connection open slots args]
  (let [resource (apply open args)
        close!   (:abort slots)
        resource (wrap-resource resource close!)
        id       (channel-id)]
    (wsr/store! connection id resource 
                :connection
                :close! close-resource! 
                :slots  resource-slots)
    id))

(def ^{:private true :dynamic true} *resource* nil)

(defn- ensure-resource []
  (if (nil? *resource*)
    (throw (IllegalStateException. "No resource in current context!"))
    true))

(defn alter-resource! [func & args]
  (ensure-resource)
  (set! *resource* (apply func *resource* args)))

(defn- read [connection read slots [id & args]]
  (binding [*resource* (get-resource connection id)]
    (let [result (apply read *resource* args)]
      (wsr/send-to! connection [id] ::update #(assoc % ::resource *resource*))
      (get-resource connection id)
      result)))

(defn- write [connection write slots [id & args]]
  (binding [*resource* (get-resource connection id)]
    (let [result (apply write *resource* args)]
      (wsr/send-to! connection [id] ::update #(assoc % ::resource *resource*))
      (get-resource connection id)
      result)))

(defn- abort [connection abort slots [id]]
  (let [id (check-channel-id id)]
    (wsr/remove! connection id)
    nil))

(defn- close [connection close slots [id]]
  (let [id        (check-channel-id id)
        resources (wsr/send-to! connection 
                                [id] 
                                ::update 
                                #(assoc % ::close! close))
        close!    (get-in resources [:contents id :resource ::close!])]
    (when-not (identical? close close!)
      (throw (IllegalStateException. "Channel closed or aborted!")))
    (wsr/remove! connection id)
    nil))

(defn- operation->keyword [operation]
  (-> operation str .trim .toLowerCase keyword))

(defn channel [slots]
  (fn [operation & args]
    (let [operation (operation->keyword operation)
          slot      (slots operation)]
      (when-not slot
        (throw (UnsupportedOperationException. 
                 (format "Channel operation not supported: '%s'!" operation))))
      ((case operation
         :open  open
         :read  read
         :write write
         :abort abort
         :close close) (req/connection) slot slots args))))

(def ^{:private true} operation-keys #{:open :read :write :abort :close})

(defmacro defchannel [name & more]
  (let [[[doc-string? attributes?] more] (split-with (complement operation-keys) 
                                                     more)
        var-meta    (if (string? doc-string?)
                      (assoc attributes? :doc doc-string?)
                      attributes?)
        target-meta {::target {::name `'~name ::type ::channel}}]
   `(def ~(vary-meta name merge var-meta target-meta)
          (vary-meta (channel (hash-map ~@more)) merge ~target-meta))))

(defn- target-name [request]
  (-> request :name str hy/hyphenate symbol))

(defn- target-finder [request]
  (let [target-name (target-name request)]
    (fn [lookup]
      (let [target (lookup target-name)]
        (if (::target (meta target))
          target)))))

(defn target-router 
  ([]
    (target-router (ns-name *ns*)))
  ([ns-name & ns-names]
    (let [ns-names   (cons ns-name ns-names)
          ns-lookups (map (fn [ns-name] 
                            (require ns-name) 
                            (ns-interns ns-name)) 
                          ns-names)]
      (req/request-router (fn [connection request]
                            (some (target-finder request) ns-lookups))))))

