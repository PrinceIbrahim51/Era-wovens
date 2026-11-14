import type { Metadata } from 'next';
import { Prose } from '@/components/Prose';

export const metadata: Metadata = { title: 'Contact' };

export default function Contact() {
  return (
    <Prose eyebrow="Help" title="Contact us" draft>
      <p>We’re happy to help with orders, bulk enquiries and styling questions.</p>
      <ul>
        <li>Email: <span className="font-mono">support@example.invalid</span> (placeholder — replace before launch)</li>
        <li>Phone / WhatsApp: to be added</li>
        <li>Hours: Monday–Saturday, 10:00–18:00 IST</li>
      </ul>
      <p>For an existing order, please have your order number (e.g. AUR-261114-ABC123) ready. You can also <a href="/track">track your order</a> online.</p>
    </Prose>
  );
}
