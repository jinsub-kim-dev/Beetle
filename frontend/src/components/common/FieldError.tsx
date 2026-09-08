/** 폼 필드 아래에 표시하는 오류 메시지. */
export function FieldError({ message }: { message?: string }) {
  if (!message) return null

  return (
    <p role="alert" className="text-destructive text-xs">
      {message}
    </p>
  )
}
