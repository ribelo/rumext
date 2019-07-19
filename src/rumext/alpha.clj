;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; Copyright (c) 2016-2019 Andrey Antukh <niwi@niwi.nz>

(ns rumext.alpha
  (:require [hicada.compiler :as hc]))

(defn- to-js-map
  "Convert a map into a JavaScript object."
  [m]
  (when-not (empty? m)
    (let [key-strs (mapv hc/to-js (keys m))
          non-str (remove string? key-strs)
          _ (assert (empty? non-str)
                    (str "Hicada: Props can't be dynamic:"
                         (pr-str non-str) "in: " (pr-str m)))
          kvs-str (->> (mapv #(-> (str \' % "':~{}")) key-strs)
                       (interpose ",")
                       (apply str))]
      (vary-meta
        (list* 'js* (str "{" kvs-str "}") (mapv identity (vals m)))
        assoc :tag 'object))))

(def handlers
  {:& (fn
        ([_ klass]
         [klass {} nil])
        ([_ klass attrs & children]
         (if (map? attrs)
           [klass (to-js-map attrs) children]
           [klass {} (cons attrs children)])))})

(defmacro html
  [body]
  (let [opts {:create-element 'rumext.alpha/create-element
              :rewrite-for? true
              :array-children? false}]
    (-> body (hicada.compiler/compile opts handlers &env))))

(defmethod hc/compile-form "letfn"
  [[_ bindings & body]]
  `(letfn ~bindings ~@(butlast body) ~(hc/emitter (last body))))

(defmethod hc/compile-form "when-let"
  [[_ bindings & body]]
  `(when-let ~bindings ~@(butlast body) ~(hc/emitter (last body))))

(defmethod hc/compile-form "if-let"
  [[_ bindings & body]]
  `(if-let ~bindings ~@(doall (for [x body] (hc/emitter x)))))

(defmethod hc/compile-form "fn"
  [[_ params & body]]
  `(fn ~params ~@(butlast body) ~(hc/emitter (last body))))

(defn parse-def
  [& {:keys [render mixins desc]
      :or {mixins [] desc ""}
      :as params}]
  (let [spec (dissoc params :mixins :render :desc)
        mixins (if (empty? spec)
                 mixins
                 (conj mixins spec))]
    [`(html ~render) desc mixins]))

(defn parse-defc
  [args]
  (loop [r {}
         s 0
         v (first args)
         n (rest args)]
    (case s
      0 (if (symbol? v)
          (recur (assoc r :name (str v)) (inc s) (first n) (rest n))
          (recur (assoc r :name "anonymous") (inc s) (first n) (rest n)))
      1 (if (string? v)
          (recur (assoc r :doc v) (inc s) (first n) (rest n))
          (recur r (inc s) v n))
      2 (if (map? v)
          (recur (assoc r :metadata v) (inc s) (first n) (rest n))
          (recur r (inc s) v n))
      3 (if (vector? v)
          (recur (assoc r :args v) (inc s) (first n) (rest n))
          (throw (ex-info "Invalid macro definition: expected component args vector" {})))
      4 (let [sym (:name r)
              args (:args r)
              func (if (map? v)
                     `(fn ~args ~v (html (do ~@n)))
                     `(fn ~args (html (do ~@(cons v n)))))]
          [func (:doc r) (:metadata r) sym]))))

(defmacro fnc
  [& args]
  (let [[render doc metadata cname] (parse-defc args)]
    `(rumext.alpha/build-fnc ~render ~cname ~metadata)))

(defmacro defc
  [& args]
  (let [[render doc metadata cname] (parse-defc args)]
    `(def ~(symbol cname) ~doc (rumext.alpha/build-fnc ~render ~cname ~metadata))))


(defmacro def
  [cname & args]
  (let [[render doc mixins] (apply parse-def args)]
    `(def ~cname ~doc (rumext.alpha/build-lazy-ctor rumext.alpha/build-def ~render ~mixins ~(str cname)))))