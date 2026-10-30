# Firebase authentication contract

The Android app keeps the existing license activation flow. Both `/activate` and
`/verify` may additionally return a fresh Firebase custom token and warehouse ID:

```json
{
  "ok": true,
  "status": "active",
  "token": "existing-license-token",
  "expires_at": 1798761600,
  "firebase_token": "firebase-custom-token",
  "warehouse_id": "es-main",
  "country_code": "ES"
}
```

Use a stable Firebase UID derived from the internal license ID. Do not use the
email as the UID because an email can change. Include these custom claims in the
Firebase token:

```json
{
  "warehouse_id": "es-main",
  "country_code": "ES",
  "license_active": true,
  "license_expires_at": 1798761600
}
```

The service-account private key used to sign Firebase custom tokens must remain
in the Worker secret store. It must never be included in the Android app or in
the repository.

The app remains compatible while the Worker does not return these two new
fields. Keep the current Firestore rules during that transition. Deploy
`firestore.rules` only after the Worker is issuing valid custom tokens and the
updated app has been installed on every active device.

`app_id` identifies the application and `warehouse_id` selects the Firestore
data scope. The planned IDs are `es-main` (primary), `us-main`, and `es-2` when
a second Spanish installation requires separate data. Multiple apps may share
`es-main` when they should share orders and reports.

The license server is shared by other applications. Warehouse fields and the
Firebase token are optional and must be omitted for licenses that do not use
Firestore. Migration `0003` assigns `es-main` only to the exact FastScan app ID
`com.ocr.fastscan`; all other existing applications remain unchanged.

The rules require a Firebase sign-in from the license server at least once every
48 hours and reject an expired `license_expires_at`. The Android app signs in
again whenever its normal startup license check succeeds.

Before enforcing Firebase App Check, register the Android release application
with Play Integrity in Firebase Console. Register the debug token printed by a
debug build only for development devices.
