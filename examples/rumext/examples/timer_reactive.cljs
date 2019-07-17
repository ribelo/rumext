(ns rumext.examples.timer-reactive
  (:require [rumext.core :as mx]
            [rumext.func :as mxf]
            [rumext.examples.util :as util]))

(def timer
  (mxf/reactive
   (mxf/fnc timer-reactive
     [props]
     (let [ts (mxf/react util/*clock)]
       [:div "Reactive" ": "
        [:span {:style {:color @util/*color}}
         (util/format-time ts)]]))))


(defn mount! [el]
  (let [comp (mx/html [:& timer {}])]
    (mx/mount comp el)))
