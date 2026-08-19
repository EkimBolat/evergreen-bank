export function Logo({ size = 40, withWordmark = false }: { size?: number; withWordmark?: boolean }) {
  return (
    <div className="flex items-center gap-2.5">
      <img src="/logo.png" alt="EverGreen Bank" width={size} height={size} className="rounded-xl" />
      {withWordmark && (
        <span className="text-lg font-extrabold tracking-tight text-brand-700">EverGreen Bank</span>
      )}
    </div>
  )
}
