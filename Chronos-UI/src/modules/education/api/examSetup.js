import { http } from '../../../api/http'
import { getDeliveryContext } from '../../../store/auth'

const root = '/admin/education/exam/setup'
const url = (path, schoolId, extra = {}) => root + path + '?' + new URLSearchParams({ schoolId, ...extra })

export const isExamCustomer = () => getDeliveryContext().mode === 'customer'
export const examHasTeaching = () => !isExamCustomer() || getDeliveryContext().modules?.includes('EDU_TEACHING_OPERATIONS')
export const examLookups = id => http.get(url('', id)).then(result => result.data)
export const examStudents = (id, keyword, page) => http.get(url('/students', id, { keyword, page }))
export const examStudentNames = (id, ids) => http.get(url('/student-names', id, { ids: ids.join(',') }))
export const saveExamSetup = (id, kind, data) => http.post(
  url(['terms', 'roster', 'account-links'].includes(kind) ? '/' + kind : '/resources/' + kind, id), data,
)
