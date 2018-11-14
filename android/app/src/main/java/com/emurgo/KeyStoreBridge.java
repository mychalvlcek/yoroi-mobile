package com.emurgo;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.hardware.fingerprint.FingerprintManager;
import android.os.CancellationSignal;

import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.BaseActivityEventListener;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;

import java.util.HashMap;
import java.util.Map;

import javax.crypto.Cipher;


public class KeyStoreBridge extends ReactContextBaseJavaModule {
    KeyguardManager keyguard;
    ReactApplicationContext context;

    int REQUEST_CODE_CONFIRM_DEVICE_CREDENTIALS = 44;

    Promise systemPinConfirmationPromise;
    Promise fingerprintConfirmationPromise;
    String systemPinKeyAlias;
    String systemPinEncryptedData;
    CancellationSignal fingerprintCancellation;
    private KeyStoreCrypto crypto;


    // encryptData
    private String REJECTION_ENCRYPTION_FAILED = "ENCRYPTION_FAILED";

    // decryptDataWithFingerprint
    private String REJECTION_ALREADY_DECRYPTING_DATA = "ALREADY_DECRYPTING_DATA";
    private String REJECTION_SENSOR_LOCKOUT = "SENSOR_LOCKOUT";
    private String REJECTION_NOT_RECOGNIZED = "NOT_RECOGNIZED";
    private String REJECTION_DECRYPTION_FAILED = "DECRYPTION_FAILED";
    // any custom code, message sent from JS

    // decryptDataWithSystemPin
    private String REJECTION_SYSTEM_AUTH_NOT_SUPPORTED = "SYSTEM_AUTH_NOT_SUPPORTED";
    private String REJECTION_FAILED_UNKNOWN_ERROR = "FAILED_UNKNOWN_ERROR";
    private String REJECTION_CANCELED = "CANCELED";
    private String REJECTION_FAILED = "FAILED";

    // deleteAndroidKeyStoreAsymmetricKeyPair
    private String REJECTION_KEY_NOT_DELETED = "KEY_NOT_DELETED";

    @Override
    public String getName() {
        return "KeyStoreBridge";
    }

    public KeyStoreBridge(ReactApplicationContext reactContext) {
        super(reactContext);
        this.context = reactContext;
        this.context.addActivityEventListener(activityEventListener);

        this.keyguard = (KeyguardManager) reactContext.getSystemService(Context.KEYGUARD_SERVICE);
        this.crypto = new KeyStoreCrypto(this.context, this.keyguard);
    }

    @Override
    public Map<String, Object> getConstants() {
        final Map<String, Object> constants = new HashMap<>();
        constants.put("AUTHENTIFICATION_VALIDITY_DURATION", this.crypto.AUTHENTIFICATION_VALIDITY_DURATION);
        return constants;
    }

    @ReactMethod
    public void initFingerprintKeys(String keyAlias, Promise promise) {
        try {
            this.crypto.createAndroidKeyStoreAsymmetricKey(keyAlias, true, false);
        } catch (Exception e) {
            promise.resolve(false);
            return;
        }

        promise.resolve(true);
    }

    @ReactMethod
    public void initSystemPinKeys(String keyAlias, Promise promise) {
        try {
            this.crypto.createAndroidKeyStoreAsymmetricKey(keyAlias, false, true);
        } catch (Exception e) {
            promise.resolve(false);
            return;
        }

        promise.resolve(true);
    }

    @ReactMethod
    public void isFingerprintEncryptionHardwareSupported(Promise promise) {
        promise.resolve(this.crypto.isFingerprintEncryptionHardwareSupported());
    }

    @ReactMethod
    public void canFingerprintEncryptionBeEnabled(Promise promise) {
        promise.resolve(this.crypto.canEnableFingerprintEncryption());
    }

    @ReactMethod
    public void isSystemPinEncryptionSupported(Promise promise) {
        promise.resolve(this.crypto.isSystemPinEncryptionSupported());
    }

    @ReactMethod
    public void isSystemAuthSupported(Promise promise) {
        promise.resolve(this.crypto.isSystemAuthSupported());
    }

    @ReactMethod
    public void encryptData(String data, String keyAlias, final Promise promise) {
        try {
            String cipherText = this.crypto.encryptData(data, keyAlias);
            promise.resolve(cipherText);
        } catch (Exception e) {
            promise.reject(REJECTION_ENCRYPTION_FAILED, REJECTION_ENCRYPTION_FAILED, e);
        }
    }

    @ReactMethod
    @TargetApi(23)
    public void cancelFingerprintScanning(String reason, final Promise promise) {
        if (fingerprintConfirmationPromise == null) {
            promise.resolve(null);
            return;
        }

        fingerprintConfirmationPromise.reject(reason, reason);
        fingerprintConfirmationPromise = null;
        if (fingerprintCancellation.isCanceled()) {
            promise.resolve(null);
            return;
        }

        fingerprintCancellation.cancel();
        promise.resolve(null);
    }

    @ReactMethod
    @TargetApi(23)
    public void decryptDataWithFingerprint(final String data, String keyAlias, final Promise promise) {
        if (fingerprintConfirmationPromise != null) {
            promise.reject(REJECTION_ALREADY_DECRYPTING_DATA, REJECTION_ALREADY_DECRYPTING_DATA);
            return;
        }
        fingerprintConfirmationPromise = promise;

        try {
            Cipher cipher = this.crypto.getDecryptCipher(keyAlias);

            this.fingerprintCancellation = new CancellationSignal();
            FingerprintManager.CryptoObject cryptoObject = new FingerprintManager.CryptoObject(cipher);
            FingerprintManager fingerprintManager = (FingerprintManager) this.context.getSystemService(Context.FINGERPRINT_SERVICE);
            fingerprintManager.authenticate(cryptoObject, fingerprintCancellation, 0, new FingerprintManager.AuthenticationCallback() {
                @Override
                public void onAuthenticationError(int errorCode, CharSequence errString) {
                    if (!fingerprintCancellation.isCanceled()) {
                        fingerprintCancellation.cancel();

                        if (errorCode == FingerprintManager.FINGERPRINT_ERROR_LOCKOUT || errorCode == FingerprintManager.FINGERPRINT_ERROR_LOCKOUT_PERMANENT) {
                            promise.reject(REJECTION_SENSOR_LOCKOUT, REJECTION_SENSOR_LOCKOUT);
                        } else {
                            promise.reject(REJECTION_NOT_RECOGNIZED, REJECTION_NOT_RECOGNIZED);
                        }
                        fingerprintConfirmationPromise = null;
                    }
                }

                @Override
                public void onAuthenticationHelp(int helpCode, CharSequence helpString) {
                    // pass this is recoverable
                }

                @Override
                public void onAuthenticationSucceeded(FingerprintManager.AuthenticationResult result) {
                    if (!fingerprintCancellation.isCanceled()) {
                        FingerprintManager.CryptoObject cipher = result.getCryptoObject();
                        try {
                            String decodedText = crypto.decryptData(data, cipher.getCipher());
                            promise.resolve(decodedText);
                        } catch (Exception e) {
                            promise.reject(REJECTION_DECRYPTION_FAILED, REJECTION_DECRYPTION_FAILED, e);
                        } finally {
                            fingerprintCancellation.cancel();
                            fingerprintConfirmationPromise = null;
                        }
                    }
                }

                @Override
                public void onAuthenticationFailed() {
                    if (fingerprintCancellation.isCanceled()) {
                        return;
                    }

                    fingerprintCancellation.cancel();
                    promise.reject(REJECTION_NOT_RECOGNIZED, REJECTION_NOT_RECOGNIZED);
                    fingerprintConfirmationPromise = null;
                }
            }, null);
        } catch (Exception e) {
            promise.reject(REJECTION_DECRYPTION_FAILED, REJECTION_DECRYPTION_FAILED, e);
            fingerprintConfirmationPromise = null;
        }
    }

    /*
        This call can be rejected with this messages:
        CANCELED
        FAILED
    */
    @ReactMethod
    public void decryptDataWithSystemPin(final String data, String keyAlias, String message, final Promise promise) {
        if (!this.crypto.isSystemAuthSupported()) {
            promise.reject(REJECTION_SYSTEM_AUTH_NOT_SUPPORTED, REJECTION_SYSTEM_AUTH_NOT_SUPPORTED);
            return;
        }
        
        Intent intent = this.keyguard.createConfirmDeviceCredentialIntent(null, message);
        if (intent != null) {
            this.systemPinConfirmationPromise = promise;
            this.systemPinKeyAlias = keyAlias;
            this.systemPinEncryptedData = data;
            this.context.startActivityForResult(intent, REQUEST_CODE_CONFIRM_DEVICE_CREDENTIALS, null);
        } else {
            promise.reject(REJECTION_FAILED_UNKNOWN_ERROR, REJECTION_FAILED_UNKNOWN_ERROR);
        }
    }

    @ReactMethod
    public void deleteAndroidKeyStoreAsymmetricKeyPair(String keyAlias, Promise promise) {
        try {
            this.crypto.deleteAndroidKeyStoreAsymmetricKeyPair(keyAlias);
            promise.resolve(true);
        } catch (Exception e) {
            promise.reject(REJECTION_KEY_NOT_DELETED, REJECTION_KEY_NOT_DELETED, e);
        }
    }

    private final ActivityEventListener activityEventListener = new BaseActivityEventListener() {
        @Override
        public void onActivityResult(Activity activity, int requestCode, int resultCode, Intent intent) {
            if (requestCode == REQUEST_CODE_CONFIRM_DEVICE_CREDENTIALS) {
                if (systemPinConfirmationPromise != null) {
                    if (resultCode == Activity.RESULT_CANCELED) {
                        systemPinConfirmationPromise.reject(REJECTION_CANCELED, REJECTION_CANCELED);
                    } else if (resultCode == Activity.RESULT_OK) {
                        try {
                            String decodedText = crypto.decryptData(systemPinEncryptedData, systemPinKeyAlias);
                            systemPinConfirmationPromise.resolve(decodedText);

                        } catch (Exception e) {
                            systemPinConfirmationPromise.reject(REJECTION_FAILED, REJECTION_FAILED, e);
                        }
                    } else {
                        systemPinConfirmationPromise.reject(REJECTION_FAILED_UNKNOWN_ERROR, REJECTION_FAILED_UNKNOWN_ERROR);
                    }

                    systemPinConfirmationPromise = null;
                    systemPinKeyAlias = null;
                    systemPinEncryptedData = null;
                }
            }
        }
    };
}