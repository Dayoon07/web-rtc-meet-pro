import { useTranslation } from 'react-i18next'
import { useChatCategoriesQuery } from '@/entities/chat'
import { Field, Select } from '@/shared/ui'

export function CategorySelect({
  teamId,
  field,
}: {
  teamId: number
  field: { id: string; value: number | null; onChange: (categoryId: number | null) => void }
}) {
  const { t } = useTranslation()
  const { data: categories = [] } = useChatCategoriesQuery(teamId)
  const { id, value, onChange } = field
  return (
    <Field label={t('chat.category')} htmlFor={id} hint={t('chat.categoryHint')}>
      <Select id={id} value={value ?? ''} onChange={(e) => onChange(e.target.value ? Number(e.target.value) : null)}>
        <option value="">{t('chat.noCategory')}</option>
        {categories.map((c) => (
          <option key={c.id} value={c.id}>
            {c.name}
          </option>
        ))}
      </Select>
    </Field>
  )
}
