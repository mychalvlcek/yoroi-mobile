// @flow
export type PasswordValidationErrors = {
  passwordReq?: boolean,
  passwordConfirmationReq?: boolean,
  matchesConfirmation?: boolean,
}

export type WalletNameValidationErrors = {
  walletNameLength?: boolean,
}

export const validatePassword = (
  password: string,
  passwordConfirmation: string,
): PasswordValidationErrors | null => {
  let validations = null
  if (!password) {
    validations = {...validations, passwordReq: true}
  }
  if (!passwordConfirmation) {
    validations = {...validations, passwordConfirmationReq: true}
  }
  if (password !== passwordConfirmation) {
    validations = {...validations, matchesConfirmation: true}
  }

  return validations
}

export const validateWalletName = (
  walletName: string
): WalletNameValidationErrors | null => {
  let validations = null
  if (walletName.length > 2 && walletName.length < 41) {
    validations = {...validations, walletNameLength: true}
  }

  return validations
}
