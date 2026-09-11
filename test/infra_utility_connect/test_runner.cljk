(require '[clojure.test :as t]
         'infra-utility-connect.methods.test-agent
         'infra-utility-connect.cells.test-utility-cell-core
         'infra-utility-connect.cells.activation-test.test-state-machine
         'infra-utility-connect.cells.meter-install.test-state-machine
         'infra-utility-connect.cells.provider-approval.test-state-machine
         'infra-utility-connect.cells.service-request.test-state-machine)

(let [result
      (t/run-tests
       'infra-utility-connect.methods.test-agent
       'infra-utility-connect.cells.test-utility-cell-core
       'infra-utility-connect.cells.activation-test.test-state-machine
       'infra-utility-connect.cells.meter-install.test-state-machine
       'infra-utility-connect.cells.provider-approval.test-state-machine
       'infra-utility-connect.cells.service-request.test-state-machine)]
  (when (pos? (+ (:fail result) (:error result)))
    (System/exit 1)))
