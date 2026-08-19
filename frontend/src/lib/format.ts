const currencyFormatter = new Intl.NumberFormat('tr-TR', {
  style: 'currency',
  currency: 'TRY',
  minimumFractionDigits: 2,
})

const dateFormatter = new Intl.DateTimeFormat('tr-TR', {
  day: '2-digit',
  month: 'short',
  hour: '2-digit',
  minute: '2-digit',
})

export function formatCurrency(amount: number): string {
  return currencyFormatter.format(amount)
}

export function formatDate(isoString: string): string {
  return dateFormatter.format(new Date(isoString))
}

export function formatAccountNumber(accountNumber: string): string {
  return accountNumber.replace(/(.{4})/g, '$1 ').trim()
}

export function accountTypeLabel(type: 'CHECKING' | 'SAVINGS'): string {
  return type === 'SAVINGS' ? 'Vadeli Hesap' : 'Vadesiz Hesap'
}
