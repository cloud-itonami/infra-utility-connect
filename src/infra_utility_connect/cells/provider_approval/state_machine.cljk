(ns infra-utility-connect.cells.provider-approval.state-machine
  "1:1 cljc port of cells/provider_approval/cell.py.
  Simple two-step state machine: init → process (complete).
  String keys mirror the Python dict structure.

  The transition table is no longer written here. It lives in
  `src/infra_utility_connect/cells/utility_cell_core.kotoba`, ships as KIR,
  and runs through `infra-utility-connect.cells.utility-cell` -- this cell,
  `service_request` and `provider_approval` were three byte-identical copies
  of one table, which is three places to correct one right answer.
  `activation_test` still carries its own copy.

  The cell keeps its own public `init-state` / `process-state` / `run-chain`
  so that when its real graph lands -- `data/cells/provider_approval.edn` already
  declares it, and it has five nodes, not two -- it stops delegating and
  nothing else moves."
  (:require [infra-utility-connect.cells.utility-cell :as uc]))

(defn init-state [state] (uc/init-state state))

(defn process-state [state] (uc/process-state state))

(defn run-chain [input-state] (uc/run-chain input-state))
