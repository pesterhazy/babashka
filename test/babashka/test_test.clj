(ns babashka.test-test
  (:require
   [babashka.impl.clojure.test :as test-impl]
   [babashka.test-utils :as tu]
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.test :as t :refer [deftest is]]
   [sci.core :as sci]))

(defn bb [& args]
  (let [sw (java.io.StringWriter.)]
    (sci/binding [test-impl/test-out sw]
      (str sw (apply tu/bb nil (map str args))))))

(deftest deftest-test
  (is (str/includes?
       (bb "(require '[clojure.test :as t]) (t/deftest foo (t/is (= 4 5))) (foo)")
       "expected: (= 4 5)\n  actual: (not (= 4 5))\n")))

(deftest run-tests-test
  (let [output (bb "(require '[clojure.test :as t]) (t/deftest foo (t/is (= 4 5))) (t/run-tests)")]
    (is (str/includes? output "Testing user"))
    (is (str/includes? output "{:test 1, :pass 0, :fail 1, :error 0, :type :summary}"))))

(deftest run-all-tests-test
  (let [output (bb "
(require '[clojure.test :as t])
(t/deftest foo (t/is (= 4 5)))
(ns foobar)
(require '[clojure.test :as t])
(t/run-all-tests)")]
    (is (str/includes? output "Testing user"))
    (is (str/includes? output "Testing foobar"))
    (is (str/includes? output "{:test 1, :pass 0, :fail 1, :error 0, :type :summary}"))))

(deftest fixtures-test
  (let [output (bb "
(require '[clojure.test :as t])
(defn once [f] (prn :once-before) (f) (prn :once-after))
(defn each [f] (prn :each-before) (f) (prn :each-after))
(t/use-fixtures :once once)
(t/use-fixtures :each each)
(t/deftest foo)
(t/deftest bar)
(t/run-tests)")]
    (is (str/includes? output (str/trim "
:once-before
:each-before
:each-after
:each-before
:each-after
:once-after")))))

(deftest with-test
  (let [output (bb "
(require '[clojure.test :as t])
(t/with-test
  (defn my-function [x y]
    (+ x y))
  (t/is (= 4 (my-function 2 2)))
  (t/is (= 7 (my-function 3 4))))
(t/run-tests)")]
    (is (str/includes? output "Ran 1 tests containing 2 assertions."))))

(deftest testing-test
  (is (str/includes? (bb "(require '[clojure.test :as t]) (t/testing \"foo\" (t/is (= 4 5)))")
                     "foo")))

(deftest are-test
  (is (str/includes? (bb "(require '[clojure.test :as t]) (t/are [x y] (= x y) 2 (+ 1 2))")
                     "expected: (= 2 (+ 1 2))")))

(deftest assert-expr-test
  (is (str/includes? (bb (.getPath (io/file "test-resources" "babashka" "assert_expr.clj")))
                     "3.14 should be roughly 3.141592653589793")))

(deftest rebind-vars-test
  (is (bb "(binding [clojure.test/report (constantly true)] nil)"))
  (is (bb "(binding [clojure.test/test-var (constantly true)] nil)")))

(deftest rebind-report-test
  (let [[m1 m2 m3]
        (edn/read-string (format "[%s]" (bb (io/file "test-resources" "babashka" "test_report.clj"))))]
    (is (= m1 '{:type :begin-test-var, :var bar}))
    (is (str/includes? (:file m2) "test_report.clj"))
    (is (= (:message m2) "1 is not equal to 2"))
    (is (= (:line m2) 6))
    (is (= m3 '{:type :end-test-var, :var bar}))))

(deftest are-with-is-test
  (let [output (bb "
(do (require '[clojure.test :as t])
(t/deftest foo (t/are [x]
(t/is (thrown-with-msg? Exception #\"\" x))
(throw (ex-info \"\" {})))))
(t/run-tests *ns*)")]
    (is (str/includes? output "Ran 1 tests containing 2 assertions."))))

(deftest test-out-test
  (let [output (bb "
(do (require '[clojure.test :as t])
(t/deftest foo (t/are [x]
(t/is (thrown-with-msg? Exception #\"\" x))
(throw (ex-info \"\" {})))))
(let [sw (java.io.StringWriter.)]
  (binding [t/*test-out* sw]
    (t/with-test-out (t/run-tests *ns*)))
    (str/includes? (str sw) \"Ran 1 tests containing 2 assertions.\"))")]
    (is (str/includes? output "true"))))

(deftest line-number-test
  (is (str/includes? (bb "test-resources/line_number_test_test.clj")
                     "line_number_test_test.clj:4")))

(deftest testing-vars-str-test
  (is (str/includes?
       (bb "(clojure.test/testing-vars-str {:file \"x\" :line 1})")
       "() (x:1)")
      "includes explicit line number + file name in test report"))

(deftest is-should-include-name-of-function-test
  (let [output (bb "(require '[clojure.test :as t]) (defn function-under-test [] (zero? nil)) (t/deftest foo (t/is (= false (function-under-test)))) (foo)")]
    (is (str/includes? output "user/function-under-test"))))

(deftest is-should-throw-wrapped-exception-assert-test
  (let [output (bb "(require '[clojure.test :as t]) (t/deftest foo (t/is (assert false))) (foo)")]
    ;; FIXME: doesn't work for assert yet
    #_(is (str/includes? output ":type :sci/error"))))

;; FIXME: handle thrown?
;; FIXME: handle thrown-with-message?
