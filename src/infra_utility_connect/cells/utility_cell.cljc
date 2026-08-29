(ns infra-utility-connect.cells.utility-cell
  "The host half of the utility cell: the open string-keyed state map, and
  nothing about which phase follows which.

  ## The split

  `cells/utility_cell_core.kotoba` owns the transition table -- the phases,
  the completion percentages, the next-node names, the `unknown` a missing
  project id reads as, and the order the two steps compose in. It is compiled
  and shipped as KIR, and `infra-utility-connect.kotoba-oracle` executes it.
  That is what runs; there is no second implementation of the table on this
  path.

  What is here is what the guest deliberately does not have: an OPEN map with
  arbitrary string keys. A LangGraph state dict carries whatever the caller
  put in it, `run-chain` merges each step's output over it, and
  `process-state` `assoc`s onto the previous `\"utility_state\"` rather than
  replacing it -- so keys nobody declared survive the chain. A record cannot
  express that and should not try. Reading the map, merging it, and renaming
  the guest's four fields onto their Python-dict spellings is this
  namespace's whole job.

  ## Three cells, one table

  `meter_install`, `service_request` and `provider_approval` all delegate
  here; before this they were three byte-identical copies of the same
  functions. `activation_test` is a fourth copy and still stands alone --
  routing it here is a one-line change to its `state_machine.cljc`.

  Each cell keeps its own `state-machine` namespace and its own public
  `init-state` / `process-state` / `run-chain`, so a cell whose real graph
  lands (`data/cells/<id>.edn` declares four graphs, and they differ) stops
  delegating without anything else moving.

  ## Equivalence

  `test/infra_utility_connect/cells/test_utility_cell_core.cljc` keeps the
  original pure-Clojure transitions as a reference implementation and requires
  the shipped guest to agree with them over a corpus. The reference is the
  check; the guest is the semantics."
  (:require [infra-utility-connect.kotoba-oracle :as oracle]))

(def ^:private core :utility-cell-core)

(def utility-state-schema
  "The guest's `UtilityState` record type, taken from a value the guest
  returns rather than written out here.

  A second copy of the field list in host source is a copy that can disagree
  with the declaration, and `ir/execute` would then refuse the argument for a
  reason that reads like a bug in the caller. Asking the guest costs one pure
  call at first use."
  (delay (oracle/descriptor
          (oracle/field (oracle/call core 'init-state [(oracle/option nil)])
                        :utility-state))))

(defn- cell-state->host
  "A guest `CellState` rendered onto the host's string keys, `assoc`ed over
  `prior` -- the previous `\"utility_state\"` map, or `{}` when the step
  builds a fresh one.

  `project-id?` says whether `\"projectId\"` is a key THIS step writes.
  `init-state` mints it; `process-state` only carries it forward, and a prior
  that never had the key must not acquire one on the way out. The guest reads
  a missing id as `unknown` so that the table has a total answer for every
  input -- turning that internal default into a host key would be this seam
  inventing state the step never observed, and it is the one place the guest
  and the pure-Clojure reference would otherwise disagree (measured; the
  parity corpus covers it)."
  [cell prior project-id?]
  (let [us (oracle/field cell :utility-state)]
    {"utility_state" (cond-> (assoc prior
                                    "phase" (oracle/field us :phase)
                                    "completionPct" (oracle/i64-value
                                                     (oracle/field us :completion-pct)))
                       project-id? (assoc "projectId"
                                          (oracle/field us :project-id)))
     "next_node" (oracle/field cell :next-node)}))

(defn- prior-utility-state [state]
  (get state "utility_state" {}))

(defn- ->prior-record
  "The previous `\"utility_state\"` as a guest record argument.

  Only the three declared fields cross; anything else the map carries stays
  here and is `assoc`ed back on the way out, which is exactly what the
  original `assoc`-onto-prior did."
  [prior]
  (oracle/record @utility-state-schema
                 [(get prior "phase" (oracle/call core 'phase-init []))
                  (get prior "projectId" (oracle/call core 'unknown-project-id []))
                  (oracle/i64 (get prior "completionPct" 0))]))

(defn init-state
  "Step 1: the cell's opening state. Reads `\"projectId\"` from the TOP level
  of `state` -- that is where the caller puts it -- and lets the guest decide
  what its absence means."
  [state]
  (cell-state->host (oracle/call core 'init-state
                                 [(oracle/option (get state "projectId"))])
                    {}
                    true))

(defn process-state
  "Step 2: advance the cell's `\"utility_state\"` to complete, preserving
  every key of it the guest does not declare."
  [state]
  (let [prior (prior-utility-state state)]
    (cell-state->host (oracle/call core 'process-state [(->prior-record prior)])
                      prior
                      (contains? prior "projectId"))))

(defn run-chain
  "The Pregel chain over an open state map: each step's output merged over
  what came in, so the caller's other top-level keys survive."
  [input-state]
  (reduce (fn [s f] (merge s (f s)))
          input-state
          [init-state process-state]))
