(ns infra-utility-connect.cells.test-utility-cell-core
  "Equivalence between the shipped guest table and a pure-Clojure reference.

  `cells/utility_cell_core.kotoba` is the semantics; it is compiled to
  `resources/infra_utility_connect/oracle/utility-cell-core.kir.edn` and that
  artifact is what `infra-utility-connect.cells.utility-cell` executes. The
  reference below is the transitions as they stood before the migration -- a
  literal copy of the four byte-identical `state_machine.cljc` bodies -- kept
  here and nowhere else, so it is a CHECK and not a second implementation
  anything can call.

  ## What makes this discriminate

  Two things, because either alone passes for the wrong reason.

  1. A corpus rather than a happy path. `run-chain` on a well-formed input
     exercises exactly one route through the table; a guest that returned a
     constant `CellState` would satisfy it. The corpus below varies the
     project id (present, absent, empty, non-ASCII), varies the prior
     `\"utility_state\"` (absent, empty, carrying undeclared keys, carrying a
     non-zero completion), and calls each step SEPARATELY as well as composed.
  2. Field-by-field assertions on the guest's own exported vocabulary, so a
     table that stopped saying `\"complete\"` fails here rather than silently
     agreeing with a host that had hard-coded the same literal.

  Breaking one branch of the `.kotoba` and regenerating the artifact turns
  this red; that was measured, in both directions, before it landed."
  (:require [clojure.test :refer [deftest is testing]]
            [infra-utility-connect.cells.utility-cell :as uc]
            [infra-utility-connect.kotoba-oracle :as oracle]))

;; -- the reference: the transitions as they were, in host Clojure -----------

(defn- ref-init-state [state]
  {"utility_state" {"phase" "init"
                    "projectId" (get state "projectId" "unknown")
                    "completionPct" 0}
   "next_node" "process"})

(defn- ref-process-state [state]
  {"utility_state" (assoc (get state "utility_state" {})
                          "phase" "complete"
                          "completionPct" 100)
   "next_node" "end"})

(defn- ref-run-chain [input-state]
  (reduce (fn [s f] (merge s (f s)))
          input-state
          [ref-init-state ref-process-state]))

;; -- the corpus -------------------------------------------------------------

(def ^:private corpus
  "Inputs chosen so that no single constant answer satisfies the suite: the
  project id and the prior utility state vary independently."
  [{}
   {"projectId" "METER-003"}
   {"projectId" ""}
   {"projectId" "日本語-ID"}
   {"projectId" "SITE-X" "extra" 7}
   {"utility_state" {}}
   {"utility_state" {"note" "keep"}}
   {"projectId" "P-1" "utility_state" {"phase" "init"
                                       "projectId" "P-1"
                                       "completionPct" 0}}
   {"utility_state" {"phase" "x" "projectId" "Z" "completionPct" 5 "note" "keep"}}
   {"projectId" "OUTER" "utility_state" {"phase" "init"
                                         "projectId" "INNER"
                                         "completionPct" 42}
    "other" {"nested" true}}])

;; -- equivalence ------------------------------------------------------------

(deftest init-state-matches-reference
  (doseq [state corpus]
    (is (= (ref-init-state state) (uc/init-state state))
        (str "init-state diverged on " (pr-str state)))))

(deftest process-state-matches-reference
  (doseq [state corpus]
    (is (= (ref-process-state state) (uc/process-state state))
        (str "process-state diverged on " (pr-str state)))))

(deftest run-chain-matches-reference
  (doseq [state corpus]
    (is (= (ref-run-chain state) (uc/run-chain state))
        (str "run-chain diverged on " (pr-str state)))))

;; -- the table's own answers, not the host's copy of them -------------------

(deftest guest-vocabulary
  (testing "the four literals come from the guest, so the host cannot agree with itself"
    (is (= "unknown" (oracle/call :utility-cell-core 'unknown-project-id [])))
    (is (= "init" (oracle/call :utility-cell-core 'phase-init [])))
    (is (= "complete" (oracle/call :utility-cell-core 'phase-complete [])))
    (is (= "process" (oracle/call :utility-cell-core 'node-process [])))
    (is (= "end" (oracle/call :utility-cell-core 'node-end [])))))

(deftest guest-run-chain-export
  (testing "the guest composes the two steps itself, not only via the host reduce"
    (let [cell (oracle/call :utility-cell-core 'run-chain [(oracle/option "R-1")])
          us (oracle/field cell :utility-state)]
      (is (= "complete" (oracle/field us :phase)))
      (is (= "R-1" (oracle/field us :project-id)))
      (is (= 100 (oracle/i64-value (oracle/field us :completion-pct))))
      (is (= "end" (oracle/field cell :next-node)))))
  (testing "a missing project id reads as unknown inside the table"
    (let [cell (oracle/call :utility-cell-core 'run-chain [(oracle/option nil)])]
      (is (= "unknown" (oracle/field (oracle/field cell :utility-state) :project-id))))))

;; -- the steps are distinguishable from each other --------------------------

(deftest steps-are-not-the-same-answer
  (testing "a core that returned one constant CellState would pass a happy-path test"
    (let [a (uc/init-state {"projectId" "A"})
          b (uc/process-state a)]
      (is (not= a b))
      (is (= "init" (get-in a ["utility_state" "phase"])))
      (is (= "complete" (get-in b ["utility_state" "phase"])))
      (is (= 0 (get-in a ["utility_state" "completionPct"])))
      (is (= 100 (get-in b ["utility_state" "completionPct"])))
      (is (= "process" (get a "next_node")))
      (is (= "end" (get b "next_node"))))))
