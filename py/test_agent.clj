#!/usr/bin/env bb
;; Clojure port of test_agent.py for infra-utility-connect agent.
(ns infra-utility-connect.py.test-agent
  "infra-utility-connect — agent gate tests (offline, no kotoba host, no network, no LLM).

  ADR-2605250900. Exercises the utility-activation constitutional gates: provider
  signature validation (G3), SLA compliance (G5), PII encryption (G11), and the
  USDC + tithe settlement (G8/G9).

  Run:  bb --classpath 20-actors 20-actors/infra-utility-connect/py/test_agent.clj"
  (:require [clojure.test :refer [deftest is run-tests testing]]
            [clojure.java.io :as io]))

;; NOTE: bb resolves ns hyphens -> underscores in classpath path lookup, but the
;; actor dir is named infra-utility-connect (hyphenated). We load-file the agent
;; directly so it is registered in its own ns, then require it by ns name.
(load-file (.getCanonicalPath
            (io/file (io/file (System/getProperty "babashka.file")) ".." "agent.clj")))
(require '[infra-utility-connect.py.agent :as agent])

;; ── G3 — provider signature validation ───────────────────────────────────────
(deftest test-provider-sig-validated
  (testing "provider signature validated (G3)"
    (let [result (agent/validate_provider_sig "Tokyo Water Bureau" "did:web:tokyo-water.go.jp")]
      (is (true? (:ok result))))))

(deftest test-provider-sig-missing
  (testing "missing provider signature rejected (G3)"
    (let [result (agent/validate_provider_sig "Tokyo Water Bureau" "")]
      (is (false? (:ok result))))))

(deftest test-provider-sig-invalid-format
  (testing "invalid sig format rejected (G3)"
    (let [result (agent/validate_provider_sig "Tokyo Water Bureau" "invalid-format-123")]
      (is (false? (:ok result))))))

;; ── G5 — SLA compliance ───────────────────────────────────────────────────────
(deftest test-sla-compliance-ok
  (testing "SLA window valid (G5)"
    (let [result (agent/check_sla_compliance "2026-06-02T00:00:00Z" "2026-06-05T00:00:00Z")]
      (is (true? (:ok result))))))

(deftest test-sla-missing-date
  (testing "missing SLA date rejected (G5)"
    (let [result (agent/check_sla_compliance "" "2026-06-05T00:00:00Z")]
      (is (false? (:ok result))))))

(deftest test-sla-invalid-format
  (testing "non-ISO-8601 format rejected (G5)"
    (let [result (agent/check_sla_compliance "2026-06-02" "2026-06-05")]
      (is (false? (:ok result))))))

;; ── G11 — PII masking ────────────────────────────────────────────────────────
(deftest test-pii-masked
  (testing "PII masked for encryption (G11)"
    (let [result (agent/mask_pii "Tanaka Yuki" "ACC-1234567890")]
      (is (contains? result :customer_masked))
      (is (contains? result :account_masked)))))

(deftest test-pii-missing-name
  (testing "missing customer name rejected (G11)"
    (let [result (agent/mask_pii "" "ACC-1234567890")]
      (is (true? (:blocked result))))))

;; ── Service request handler ───────────────────────────────────────────────────
(deftest test-service-request-mep-incomplete
  (testing "incomplete MEP signoff rejected"
    (let [incomplete-mep {"water" true "gas" false "electric" true}
          result (agent/handle_service_request incomplete-mep "35.6595,139.7004")]
      (is (true? (:blocked result))))))

(deftest test-service-request-all-providers
  (testing "complete MEP generates 4 service requests"
    (let [complete-mep {"water" true "gas" true "electric" true "telecom" true}
          result (agent/handle_service_request complete-mep "35.6595,139.7004")]
      (is (= 4 (count (:service_requests result)))))))

;; ── Provider approval handler ─────────────────────────────────────────────────
(deftest test-provider-approval-signatures
  (testing "provider approvals validated (G3)"
    (let [result (agent/handle_provider_approval ["req.water.001" "req.gas.001"])
          approvals (:approvals result)]
      (is (every? #(= "approved" (:status %)) approvals)))))

;; ── Meter install handler ─────────────────────────────────────────────────────
(deftest test-meter-install-calibrated
  (testing "meters returned calibrated (G2)"
    (let [result (agent/handle_meter_install ["appr.water.001"])
          meters (:meters result)]
      (is (pos? (count meters)))
      (is (= "calibrated" (:status (first meters)))))))

;; ── Activation test handler ───────────────────────────────────────────────────
(deftest test-activation-all-live
  (testing "all 4 services confirmed live"
    (let [result (agent/handle_activation_test [{:serial "WM-JP-2026-001"}])]
      (is (true? (:water_live result)))
      (is (true? (:gas_live result)))
      (is (true? (:electric_live result)))
      (is (true? (:telecom_live result)))
      (is (= "pass" (:result result))))))

;; ── Settlement tithe split ────────────────────────────────────────────────────
(deftest test-settlement-tithe-split
  (testing "10% tithe + stops at intent (G8/G9)"
    (let [s (agent/build_settlement_intent 100000000)]
      (is (= 10000000 (:titheMinor s)))
      (is (= "intent" (:state s)))
      (is (= "usdc-base-l2" (:rail s))))))

(deftest test-settlement-executed-with-sig
  (testing "settlement executes only with member signature (G9)"
    (let [s (agent/build_settlement_intent 100000000 "did:key:z6Mk123")]
      (is (= "executed" (:state s))))))

;; ── Entry point ──────────────────────────────────────────────────────────────
(when (= *file* (System/getProperty "babashka.file"))
  (let [{:keys [fail error]} (clojure.test/run-tests 'infra-utility-connect.py.test-agent)]
    (System/exit (if (zero? (+ fail error)) 0 1))))
