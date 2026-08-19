import { ApiError } from './api'

/**
 * The backend's exception messages are in English. Translate the ones reachable
 * from this frontend's flows so the user never sees raw English text; anything
 * unrecognized falls through as-is rather than being silently hidden.
 */
const TRANSLATIONS: Array<[RegExp, string]> = [
  [/insufficient balance/i, 'Bakiye yetersiz.'],
  [/cannot transfer to the same account/i, 'Kendi hesabınıza transfer yapamazsınız.'],
  [/daily withdrawal limit exceeded/i, 'Günlük çekim limitiniz aşıldı.'],
  [/monthly withdrawal limit exceeded/i, 'Aylık çekim limitiniz aşıldı.'],
  [/invalid national id or password/i, 'T.C. Kimlik No veya şifre hatalı.'],
  [/invalid two-factor code/i, 'Doğrulama kodu hatalı.'],
  [/invalid or expired two-factor session/i, 'Doğrulama oturumunun süresi doldu, lütfen tekrar giriş yapın.'],
  [/two-factor authentication is already enabled/i, 'İki adımlı doğrulama zaten açık.'],
  [/too many failed login attempts/i, 'Çok fazla başarısız deneme yapıldı. Lütfen daha sonra tekrar deneyin.'],
  [/email already in use/i, 'Bu e-posta adresi zaten kullanımda.'],
  [/already has a login account/i, 'Bu müşterinin zaten bir giriş bilgisi var.'],
  [/national id already registered/i, 'Bu T.C. Kimlik No zaten kayıtlı.'],
  [/refresh token has expired/i, 'Oturumunuzun süresi doldu, lütfen tekrar giriş yapın.'],
  [/account not found/i, 'Hesap bulunamadı.'],
  [/customer not found/i, 'Müşteri bulunamadı.'],
  [/amount must be greater than zero/i, 'Tutar sıfırdan büyük olmalıdır.'],
]

export function translateErrorMessage(message: string): string {
  const match = TRANSLATIONS.find(([pattern]) => pattern.test(message))
  return match ? match[1] : message
}

export function friendlyErrorMessage(err: unknown, fallback: string): string {
  if (err instanceof ApiError) {
    return translateErrorMessage(err.message)
  }
  return fallback
}
