# Go-live checklist for real payments (NOT approved)

This build is hard-wired to Razorpay **Test Mode**: `payment-service` refuses to start if `RAZORPAY_MODE` is not `test` or the key id is not `rzp_test_…`. Enabling live payments requires a reviewed code change plus every item below.

- [ ] Business approval and Razorpay KYC / live account activation
- [ ] Legal review: terms, privacy, returns, shipping, GST invoicing, consumer-protection and data-protection obligations
- [ ] `auth-service` and `admin-service` with MFA, RBAC and immutable audit log in place
- [ ] mTLS or workload identity between services; network policies
- [ ] Independent penetration test with all critical/high findings fixed and retested
- [ ] OWASP ZAP against staging, load test of checkout and payment, Lighthouse/a11y pass
- [ ] Secrets in a managed secret store; production and test keys/environments fully isolated
- [ ] Webhook endpoint on HTTPS with a separate live webhook secret; replay and reconciliation monitoring
- [ ] Backups encrypted and a restore drill completed
- [ ] Monitoring, alerting and an incident-response runbook for payment failures and reconciliation drift
- [ ] Refunds workflow implemented and tested
