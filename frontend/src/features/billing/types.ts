/** One charge on a bill (backend: InvoiceLineResponse). */
export interface InvoiceLine {
  kind: 'CONSULTATION' | 'MEDICINE';
  description: string;
  quantity: number;
  unitPriceKes: number;
  amountKes: number;
}

/** A visit's bill (backend: InvoiceResponse). */
export interface Invoice {
  id: string;
  visitId: string;
  status: 'UNPAID' | 'PAID' | 'INSURANCE_PENDING';
  totalKes: number;
  paymentMethod: 'CASH' | 'INSURANCE' | 'MPESA' | null;
  lines: InvoiceLine[];
}

export type SettleMethod = 'cash' | 'insurance';
