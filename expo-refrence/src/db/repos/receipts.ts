import { getDatabase } from '../init';

const RECEIPT_TYPE = {
  DELIVERED: 'delivered',
  READ: 'read',
} as const;

export type ReceiptType =
  (typeof RECEIPT_TYPE)[keyof typeof RECEIPT_TYPE];

async function addReceipt(
  messageId: string,
  receiptType: ReceiptType,
): Promise<void> {
  await getDatabase().execute(
    `
      INSERT OR IGNORE INTO message_receipts (
        message_id,
        receipt_type,
        created_at
      )
      VALUES (?, ?, ?)
    `,
    [messageId, receiptType, Math.floor(Date.now() / 1000)],
  );
}

export const receiptsRepo = {
  async addDelivered(messageId: string): Promise<void> {
    await addReceipt(messageId, RECEIPT_TYPE.DELIVERED);
  },

  async addRead(messageId: string): Promise<void> {
    await addReceipt(messageId, RECEIPT_TYPE.READ);
  },

  async listForMessage(messageId: string): Promise<ReceiptType[]> {
    const result = await getDatabase().execute(
      `
        SELECT receipt_type
        FROM message_receipts
        WHERE message_id = ?
      `,
      [messageId],
    );

    return result.rows
      .map((row) => row.receipt_type)
      .filter((receiptType): receiptType is ReceiptType => (
        receiptType === RECEIPT_TYPE.DELIVERED || receiptType === RECEIPT_TYPE.READ
      ));
  },

  async hasDelivered(messageId: string): Promise<boolean> {
    const receipts = await this.listForMessage(messageId);

    return receipts.includes(RECEIPT_TYPE.DELIVERED);
  },

  async hasRead(messageId: string): Promise<boolean> {
    const receipts = await this.listForMessage(messageId);

    return receipts.includes(RECEIPT_TYPE.READ);
  },
};
