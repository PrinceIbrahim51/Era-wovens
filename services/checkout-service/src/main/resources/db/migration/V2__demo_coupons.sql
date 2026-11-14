-- Demo coupons for development and test environments.
INSERT INTO coupons (code, description, kind, value, max_discount_paise, min_subtotal_paise, applies_to, usage_limit)
VALUES ('WELCOME10', '10% off your first order over ₹2,000 (up to ₹1,000)', 'PERCENT', 1000, 100000, 200000, 'REGULAR', 10000),
       ('FESTIVE500', '₹500 off orders over ₹5,000', 'FLAT', 50000, NULL, 500000, 'ALL', 10000),
       ('BULK5', '5% off bulk orders over ₹15,000 (up to ₹10,000)', 'PERCENT', 500, 1000000, 1500000, 'BULK', 1000);
INSERT INTO coupons (code, description, kind, value, min_subtotal_paise, applies_to, active)
VALUES ('EXPIRED20', 'Inactive demo coupon', 'PERCENT', 2000, 0, 'ALL', false);
