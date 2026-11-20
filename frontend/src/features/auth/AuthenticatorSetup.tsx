import QRCode from 'qrcode';
import { useEffect, useState } from 'react';
import { secretInGroups } from './otpauth';

interface AuthenticatorSetupProps {
  /** The otpauth:// link from the password step. */
  otpauthUri: string;
}

/**
 * First login: the QR code to scan into an authenticator app, and the same key as text.
 * The QR code is drawn here in the browser, so the secret never goes to another service.
 */
export function AuthenticatorSetup({ otpauthUri }: AuthenticatorSetupProps) {
  const [qrCode, setQrCode] = useState<string | null>(null);

  useEffect(() => {
    let current = true;
    QRCode.toString(otpauthUri, { type: 'svg', margin: 1, width: 220 })
      .then((svg) => {
        if (current) {
          setQrCode(`data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`);
        }
      })
      .catch(() => {
        if (current) {
          setQrCode(null);
        }
      });
    return () => {
      current = false;
    };
  }, [otpauthUri]);

  return (
    <section aria-labelledby="setup-heading">
      <h2 id="setup-heading">Set up your authenticator app</h2>
      <ol>
        <li>Open an authenticator app on your phone (for example Google Authenticator).</li>
        <li>Scan this code, or type in the key below.</li>
        <li>Enter the 6-digit code the app shows.</li>
      </ol>
      {qrCode ? (
        <img
          src={qrCode}
          width={220}
          height={220}
          alt="QR code to scan with your authenticator app"
        />
      ) : null}
      <p>
        Key: <code className="secret">{secretInGroups(otpauthUri)}</code>
      </p>
    </section>
  );
}
