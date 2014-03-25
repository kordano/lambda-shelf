(ns lambda-shelf.core-test
  (:require [clojure.test :refer :all]
            [lambda-shelf.core :refer :all]))




(deftest test-bookmark-dispatch
  (testing "Correct dispatch behaviour"
    (let [fetch-url-title-request {:type :fetch-title :data {:url "http://www.youtube.com/watch?v=z5rRZdiu1UE-"}}]
      (is (= {:title "Beastie Boys - Sabotage - YouTube"} (dispatch-bookmark test-request)))
      (is (= "DEFAULT" (dispatch-bookmark {}))))))
