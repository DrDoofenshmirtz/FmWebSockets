(ns
  ^{:doc 
  
  "Support for the convenient definition of targets for remote procedure calls."
    
    :author "Frank Mosebach"}
  fm.websockets.rpc.targets
  (:require
    [fm.core.hyphenate :as hy]
    [fm.resources.core :as rsc]
    [fm.websockets.resources :as wsr]
    [fm.websockets.rpc.request :as req])
  (:import
    (java.util UUID)))

(defmacro defaction [name & more]
  (let [[[doc-string? attributes?] more] (split-with (complement sequential?) 
                                                     more)
        var-meta    (if (string? doc-string?)
                      (assoc attributes? :doc doc-string?)
                      attributes?)
        target-meta {::target {::name `'~name ::type ::action}}]    
   `(def ~(vary-meta (symbol (str name)) merge var-meta target-meta)
          (vary-meta (fn ~name ~@more) merge ~target-meta))))

(defn- operation->keyword [operation]
  (-> operation str .trim .toLowerCase keyword))

(defn- wrap-resource [resource close!]
  {::resource resource
   ::close!   close!})

(defn- update-resource [wrapped-resource resource]
  (-> wrapped-resource (assoc ::resource resource) rsc/good))

(defn- remove-resource [wrapped-resource close!]
  (-> wrapped-resource (assoc ::close! close!) rsc/expired))

(defn- close-resource! [{resource ::resource close! ::close!}]
  (close! resource))

(def ^{:private true} resource-slots {::update update-resource 
                                      ::remove remove-resource})

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

(defn- receive [connection receive slots [id & args]]
  (let [resource (get-resource connection id)
        resource (apply receive resource args)]
    (wsr/send-to! connection [id] ::update resource)
    (get-resource connection id)
    id))
 
(defn- abort [connection abort slots [id]]
  (let [id (check-channel-id id)]
    (wsr/remove! connection id)
    nil))

(defn- close [connection close slots [id]]
  (let [id (check-channel-id id)]))

(defn channel [slots]
  (fn [operation & args]
    (let [operation (operation->keyword operation)
          slot      (slots operation)]
      (when-not slot
        (throw (IllegalArgumentException. 
                 (format "Illegal channel operation: '%s'!" operation))))
      ((case operation
         :open    open
         :receive receive
         :abort   abort
         :close   close) (req/connection) slot slots args))))

(defmacro defchannel [name & more])

(defn- target-name [request]
  (-> request :name symbol hy/hyphenate))

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

