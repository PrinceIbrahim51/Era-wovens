'use client';
import Link from 'next/link';
import { useRouter, useSearchParams } from 'next/navigation';
import { useCallback, useEffect, useRef, useState } from 'react';
import { useCartSummary, useOrderingOpen, useToast } from '@/components/Providers';
import { OrderingNotOpen } from '@/app/cart/CartView';
import { api, ApiError } from '@/lib/api';
import { formatPaise } from '@/lib/format';
import { COD_REASONS } from '@/lib/messages';
import type { CartType, Quote } from '@/lib/types';

const STATES = ['Andaman and Nicobar Islands', 'Andhra Pradesh', 'Arunachal Pradesh', 'Assam', 'Bihar', 'Chandigarh', 'Chhattisgarh',
  'Dadra and Nagar Haveli and Daman and Diu', 'Delhi', 'Goa', 'Gujarat', 'Haryana', 'Himachal Pradesh', 'Jammu and Kashmir', 'Jharkhand',
  'Karnataka', 'Kerala', 'Ladakh', 'Lakshadweep', 'Madhya Pradesh', 'Maharashtra', 'Manipur', 'Meghalaya', 'Mizoram', 'Nagaland', 'Odisha',
  'Puducherry', 'Punjab', 'Rajasthan', 'Sikkim', 'Tamil Nadu', 'Telangana', 'Tripura', 'Uttar Pradesh', 'Uttarakhand', 'West Bengal'];

type Form = { fullName: string; email: string; phone: string; line1: string; line2: string; landmark: string; city: string; state: string; pincode: string };
const EMPTY: Form = { fullName: '', email: '', phone: '', line1: '', line2: '', landmark: '', city: '', state: '', pincode: '' };

function validate(f: Form): Partial<Record<keyof Form, string>> {
  const e: Partial<Record<keyof Form, string>> = {};
  if (f.fullName.trim().length < 2) e.fullName = 'Enter your full name.';
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(f.email.trim())) e.email = 'Enter a valid email address.';
  if (!/^[6-9]\d{9}$/.test(f.phone)) e.phone = 'Enter a 10-digit Indian mobile number.';
  if (!f.line1.trim()) e.line1 = 'Enter your house number and street.';
  if (!f.city.trim()) e.city = 'Enter your city.';
  if (!STATES.includes(f.state)) e.state = 'Choose your state.';
  if (!/^[1-9]\d{5}$/.test(f.pincode)) e.pincode = 'Enter a valid 6-digit PIN code.';
  return e;
}

const SERVER_FIELD: Record<string, keyof Form> = {
  'contact.fullName': 'fullName', 'contact.email': 'email', 'contact.phone': 'phone', 'shippingAddress.line1': 'line1',
  'shippingAddress.line2': 'line2', 'shippingAddress.landmark': 'landmark', 'shippingAddress.city': 'city', 'shippingAddress.pincode': 'pincode',
};

export function CheckoutForm() {
  return useOrderingOpen() ? <CheckoutFormInner /> : <OrderingNotOpen />;
}

function CheckoutFormInner() {
  const params = useSearchParams();
  const router = useRouter();
  const toast = useToast();
  const { refresh } = useCartSummary();
  const cartType: CartType = params.get('type') === 'bulk' ? 'BULK' : 'REGULAR';
  const [form, setForm] = useState<Form>(EMPTY);
  const [touched, setTouched] = useState<Partial<Record<keyof Form, boolean>>>({});
  const [serverErrors, setServerErrors] = useState<Partial<Record<keyof Form, string>>>({});
  const [couponInput, setCouponInput] = useState('');
  const [coupon, setCoupon] = useState('');
  const [quote, setQuote] = useState<Quote | null>(null);
  const [quoteError, setQuoteError] = useState<ApiError | null>(null);
  const [quoting, setQuoting] = useState(false);
  const [method, setMethod] = useState<'COD' | 'ONLINE'>('ONLINE');
  const [onlineEnabled, setOnlineEnabled] = useState<boolean | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [banner, setBanner] = useState<string | null>(null);
  const idemKey = useRef<string>('');

  const errors = { ...validate(form), ...serverErrors };
  const show = (k: keyof Form) => (touched[k] || serverErrors[k] ? errors[k] : undefined);

  useEffect(() => {
    api<{ onlineEnabled: boolean }>('/api/v1/payments/config').then((c) => {
      setOnlineEnabled(c.onlineEnabled);
      if (!c.onlineEnabled) setMethod('COD');
    }).catch(() => { setOnlineEnabled(false); setMethod('COD'); });
  }, []);

  const requestQuote = useCallback(async (pincode: string, couponCode: string) => {
    setQuoting(true);
    setQuoteError(null);
    try {
      const q = await api<Quote>('/api/v1/checkout/quotes', { method: 'POST', json: { cartType, pincode, couponCode: couponCode || undefined } });
      setQuote(q);
      idemKey.current = crypto.randomUUID();
      if (!q.cod.eligible) setMethod((m) => (m === 'COD' && onlineEnabled ? 'ONLINE' : m));
    } catch (e) {
      setQuote(null);
      setQuoteError(e instanceof ApiError ? e : new ApiError(0, 'ERROR', 'Could not calculate your total.'));
    } finally {
      setQuoting(false);
    }
  }, [cartType, onlineEnabled]);

  useEffect(() => {
    if (!/^[1-9]\d{5}$/.test(form.pincode)) { setQuote(null); return; }
    const t = window.setTimeout(() => requestQuote(form.pincode, coupon), 350);
    return () => window.clearTimeout(t);
  }, [form.pincode, coupon, requestQuote]);

  const set = (k: keyof Form) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => {
    let v = e.target.value;
    if (k === 'phone') v = v.replace(/\D/g, '').replace(/^91(?=\d{10}$)/, '').slice(0, 10);
    if (k === 'pincode') v = v.replace(/\D/g, '').slice(0, 6);
    setForm((f) => ({ ...f, [k]: v }));
    setServerErrors((s) => ({ ...s, [k]: undefined }));
  };

  async function placeOrder(e: React.FormEvent) {
    e.preventDefault();
    setBanner(null);
    setTouched(Object.fromEntries(Object.keys(EMPTY).map((k) => [k, true])));
    if (Object.keys(validate(form)).length) {
      document.querySelector<HTMLElement>('[aria-invalid="true"]')?.focus();
      return;
    }
    if (!quote) { setBanner('We need a valid delivery PIN code to calculate your total.'); return; }
    if (new Date(quote.expiresAt).getTime() < Date.now()) {
      await requestQuote(form.pincode, coupon);
      setBanner('Your total was refreshed. Please review and place your order.');
      return;
    }
    setSubmitting(true);
    try {
      const res = await api<{ orderId: string; status: string; paymentMethod: string }>('/api/v1/checkout/sessions', {
        method: 'POST',
        headers: { 'Idempotency-Key': idemKey.current },
        json: {
          quoteId: quote.quoteId,
          paymentMethod: method,
          contact: { fullName: form.fullName.trim(), email: form.email.trim(), phone: form.phone },
          shippingAddress: { line1: form.line1.trim(), line2: form.line2.trim() || undefined, landmark: form.landmark.trim() || undefined, city: form.city.trim(), state: form.state, pincode: form.pincode },
        },
      });
      refresh();
      router.push(`/orders/${res.orderId}?${res.paymentMethod === 'ONLINE' ? 'pay=1' : 'placed=1'}`);
    } catch (err) {
      const ae = err instanceof ApiError ? err : new ApiError(0, 'ERROR', 'Something went wrong.');
      if (ae.code === 'QUOTE_CHANGED' && ae.body.quote) {
        setQuote(ae.body.quote as Quote);
        idemKey.current = crypto.randomUUID();
        setBanner('Prices, stock or offers changed since you started. Please review the updated total and place your order again.');
      } else if (ae.code === 'QUOTE_EXPIRED') {
        await requestQuote(form.pincode, coupon);
        setBanner('Your total was refreshed. Please review and place your order.');
      } else if (ae.code === 'VALIDATION_FAILED' && ae.body.fields) {
        const mapped: Partial<Record<keyof Form, string>> = {};
        for (const [k, v] of Object.entries(ae.body.fields as Record<string, string>)) { const f = SERVER_FIELD[k]; if (f) mapped[f] = `Please check this field (${v}).`; }
        setServerErrors(mapped);
        setBanner('Please correct the highlighted fields.');
      } else {
        if (['INSUFFICIENT_STOCK', 'CART_INVALID', 'EMPTY_CART', 'BULK_MINIMUM_NOT_MET'].includes(ae.code)) idemKey.current = crypto.randomUUID();
        setBanner(ae.message);
      }
      window.scrollTo({ top: 0, behavior: 'smooth' });
    } finally {
      setSubmitting(false);
    }
  }

  const codTotal = quote?.cod.eligible ? quote.cod.totalPaise : undefined;
  const payable = method === 'COD' ? codTotal : quote?.totalPaise;

  const field = (k: keyof Form, label: string, props: React.InputHTMLAttributes<HTMLInputElement> = {}) => (
    <div>
      <label htmlFor={`f-${k}`} className="label">{label}{props.required === false ? <span className="font-normal text-muted"> (optional)</span> : null}</label>
      <input id={`f-${k}`} value={form[k]} onChange={set(k)} onBlur={() => setTouched((t) => ({ ...t, [k]: true }))}
        aria-invalid={!!show(k)} aria-describedby={show(k) ? `e-${k}` : undefined} className="field" {...props} required={props.required !== false} />
      {show(k) && <p id={`e-${k}`} className="mt-1.5 text-sm text-danger">{show(k)}</p>}
    </div>
  );

  return (
    <form onSubmit={placeOrder} noValidate className="grid gap-12 lg:grid-cols-[1fr_26rem]">
      <div>
        <p className="eyebrow">{cartType === 'BULK' ? 'Bulk order checkout' : 'Checkout'}</p>
        <h1 className="mt-2 font-display text-5xl text-plum">Delivery & payment</h1>
        {banner && <div role="alert" className="mt-6 rounded-xl border border-danger/30 bg-white px-5 py-4 text-sm text-danger">{banner}</div>}

        <fieldset className="mt-10">
          <legend className="font-display text-2xl text-plum">Contact</legend>
          <div className="mt-5 grid gap-5 sm:grid-cols-2">
            <div className="sm:col-span-2">{field('fullName', 'Full name', { autoComplete: 'name', maxLength: 80 })}</div>
            {field('email', 'Email', { type: 'email', autoComplete: 'email', maxLength: 254 })}
            {field('phone', 'Mobile number', { type: 'tel', inputMode: 'numeric', autoComplete: 'tel-national', placeholder: '10-digit mobile' })}
          </div>
          <p className="mt-3 text-xs text-muted">We use these only for order updates and delivery. Guest checkout — no account needed.</p>
        </fieldset>

        <fieldset className="mt-10">
          <legend className="font-display text-2xl text-plum">Delivery address</legend>
          <div className="mt-5 grid gap-5 sm:grid-cols-2">
            <div className="sm:col-span-2">{field('line1', 'House / flat and street', { autoComplete: 'address-line1', maxLength: 120 })}</div>
            <div className="sm:col-span-2">{field('line2', 'Area / locality', { autoComplete: 'address-line2', maxLength: 120, required: false })}</div>
            {field('landmark', 'Landmark', { maxLength: 80, required: false })}
            {field('city', 'City', { autoComplete: 'address-level2', maxLength: 60 })}
            <div>
              <label htmlFor="f-state" className="label">State / UT</label>
              <select id="f-state" value={form.state} onChange={set('state')} onBlur={() => setTouched((t) => ({ ...t, state: true }))}
                aria-invalid={!!show('state')} aria-describedby={show('state') ? 'e-state' : undefined} className="field" autoComplete="address-level1" required>
                <option value="">Select…</option>
                {STATES.map((s) => <option key={s}>{s}</option>)}
              </select>
              {show('state') && <p id="e-state" className="mt-1.5 text-sm text-danger">{show('state')}</p>}
            </div>
            {field('pincode', 'PIN code', { inputMode: 'numeric', autoComplete: 'postal-code', placeholder: '6 digits' })}
          </div>
        </fieldset>

        <fieldset className="mt-10" aria-describedby="pay-note">
          <legend className="font-display text-2xl text-plum">Payment</legend>
          <div className="mt-5 grid gap-3">
            <label className={`flex cursor-pointer items-start gap-4 rounded-xl border p-5 transition ${method === 'ONLINE' ? 'border-plum bg-white' : 'border-line'} ${!onlineEnabled ? 'cursor-not-allowed opacity-60' : ''}`}>
              <input type="radio" name="pm" value="ONLINE" checked={method === 'ONLINE'} disabled={!onlineEnabled} onChange={() => setMethod('ONLINE')} className="mt-1 accent-[var(--color-plum)]" />
              <span>
                <span className="font-semibold">Pay online <span className="ml-2 rounded-full bg-gold/25 px-2 py-0.5 text-[0.65rem] font-bold uppercase tracking-wider text-gold-deep">Razorpay test mode</span></span>
                <span className="mt-1 block text-sm text-muted">{onlineEnabled === false ? 'Online payments are not configured in this environment.' : 'UPI, cards and netbanking via Razorpay’s test checkout. No real money is charged.'}</span>
              </span>
            </label>
            <label className={`flex cursor-pointer items-start gap-4 rounded-xl border p-5 transition ${method === 'COD' ? 'border-plum bg-white' : 'border-line'} ${quote && !quote.cod.eligible ? 'cursor-not-allowed opacity-60' : ''}`}>
              <input type="radio" name="pm" value="COD" checked={method === 'COD'} disabled={!!quote && !quote.cod.eligible} onChange={() => setMethod('COD')} className="mt-1 accent-[var(--color-plum)]" />
              <span>
                <span className="font-semibold">Cash on delivery{quote?.cod.eligible && quote.cod.feePaise > 0 ? <span className="font-normal text-muted"> · {formatPaise(quote.cod.feePaise)} COD fee</span> : null}</span>
                <span className="mt-1 block text-sm text-muted">
                  {quote && !quote.cod.eligible ? quote.cod.reasons.map((r) => COD_REASONS[r] ?? r).join(' ') : 'Pay in cash when your order arrives. Availability depends on PIN code and order value.'}
                </span>
              </span>
            </label>
          </div>
          <p id="pay-note" className="mt-3 text-xs text-muted">Your order is confirmed only after our server verifies the payment or COD eligibility.</p>
        </fieldset>
      </div>

      <aside className="card h-fit p-6 lg:sticky lg:top-28" aria-label="Order summary" aria-busy={quoting}>
        <h2 className="font-display text-2xl text-plum">Order summary</h2>
        {!quote && !quoteError && (
          <p className="mt-4 text-sm text-muted">{quoting ? 'Calculating your total…' : 'Enter your delivery PIN code to see shipping, tax and your total.'}</p>
        )}
        {quoteError && (
          <div className="mt-4 rounded-lg bg-ivory-deep p-4 text-sm" role="alert">
            <p className="text-danger">{quoteError.message}</p>
            {['BULK_MINIMUM_NOT_MET', 'CART_INVALID', 'INSUFFICIENT_STOCK', 'EMPTY_CART'].includes(quoteError.code) && (
              <Link href={cartType === 'BULK' ? '/cart?type=bulk' : '/cart'} className="mt-2 inline-block font-semibold underline">Review your bag</Link>
            )}
          </div>
        )}
        {quote && (
          <div className={quoting ? 'opacity-60 transition' : 'transition'}>
            <ul className="mt-4 space-y-3 text-sm">
              {quote.lines.map((l) => (
                <li key={l.sku} className="flex justify-between gap-3">
                  <span>{l.productName} <span className="text-muted">· {l.colorName} × {l.quantity}</span></span>
                  <span className="shrink-0">{formatPaise(l.lineTotalPaise)}</span>
                </li>
              ))}
            </ul>
            <div className="zari-rule my-5" aria-hidden />
            <dl className="space-y-2 text-sm">
              <div className="flex justify-between"><dt>Subtotal</dt><dd>{formatPaise(quote.subtotalPaise)}</dd></div>
              {quote.discountPaise > 0 && <div className="flex justify-between text-success"><dt>Discount ({quote.coupon?.code})</dt><dd>−{formatPaise(quote.discountPaise)}</dd></div>}
              <div className="flex justify-between"><dt>Shipping</dt><dd>{quote.shippingPaise ? formatPaise(quote.shippingPaise) : 'Free'}</dd></div>
              {method === 'COD' && quote.cod.eligible && <div className="flex justify-between"><dt>COD fee</dt><dd>{formatPaise(quote.cod.feePaise)}</dd></div>}
              <div className="flex justify-between border-t border-line pt-3 text-base font-semibold"><dt>Total</dt><dd>{payable !== undefined ? formatPaise(payable) : '—'}</dd></div>
              <div className="flex justify-between text-xs text-muted"><dt>Includes GST of</dt><dd>{formatPaise(quote.taxIncludedPaise, true)}</dd></div>
            </dl>
          </div>
        )}
        <div className="mt-6">
          <label htmlFor="coupon" className="label">Coupon code</label>
          <div className="flex gap-2">
            <input id="coupon" value={couponInput} maxLength={20} onChange={(e) => setCouponInput(e.target.value.toUpperCase().replace(/[^A-Z0-9]/g, ''))} className="field py-2.5" placeholder="e.g. WELCOME10" />
            <button type="button" className="btn-outline px-4" disabled={!couponInput || !/^[1-9]\d{5}$/.test(form.pincode)} onClick={() => setCoupon(couponInput)}>Apply</button>
          </div>
          {quote?.coupon && (
            <p className={`mt-2 text-sm ${quote.coupon.applied ? 'text-success' : 'text-danger'}`} role="status">
              {quote.coupon.applied ? `Applied: ${quote.coupon.message}` : quote.coupon.message}
              {coupon && <button type="button" className="ml-2 underline" onClick={() => { setCoupon(''); setCouponInput(''); }}>Remove</button>}
            </p>
          )}
        </div>
        <button type="submit" className="btn-primary mt-6 w-full py-4" disabled={submitting || quoting || !quote}>
          {submitting ? 'Placing order…' : method === 'ONLINE' ? 'Continue to test payment' : 'Place COD order'}
        </button>
        {quote && <p className="mt-3 text-center text-xs text-muted">Quote valid until {new Date(quote.expiresAt).toLocaleTimeString('en-IN', { hour: '2-digit', minute: '2-digit' })}</p>}
      </aside>
    </form>
  );
}
