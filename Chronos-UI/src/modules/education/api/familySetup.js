import * as admin from '../../../api/admin'
import { http } from '../../../api/http'
import { getDeliveryContext } from '../../../store/auth'

const root = '/admin/education/home-school/setup'
const query = (params = {}) => new URLSearchParams(Object.entries(params)
  .filter(([, value]) => value !== undefined && value !== null && value !== ''))

export const usesFamilySetup = () => {
  const { mode, modules } = getDeliveryContext()
  return mode === 'customer' && (modules || []).includes('EDU_HOME_SCHOOL')
}

export const listAdministrativeClasses = params => usesFamilySetup()
  ? http.get(`${root}/classes?${query(params)}`) : admin.listAdministrativeClasses(params)
export const listEducationStudents = params => usesFamilySetup()
  ? http.get(`${root}/students?${query({ size: 20, ...params })}`) : admin.listEducationStudents(params)
export const listEducationParents = params => usesFamilySetup()
  ? http.get(`${root}/parents?${query(params)}`) : admin.listEducationParents(params)
export const dictionaryOptions = code => usesFamilySetup()
  ? http.get(`${root}/dictionaries/${encodeURIComponent(code)}`) : admin.dictionaryOptions(code)
export const createEducationParent = input => usesFamilySetup()
  ? http.post(`${root}/parents`, input) : admin.createEducationParent(input)
export const updateEducationParent = (id, input) => usesFamilySetup()
  ? http.put(`${root}/parents/${id}`, input) : admin.updateEducationParent(id, input)
export const deleteEducationParent = id => usesFamilySetup()
  ? http.delete(`${root}/parents/${id}`) : admin.deleteEducationParent(id)
export const listStudentGuardians = params => usesFamilySetup()
  ? http.get(`${root}/guardians?${query(params)}`) : admin.listStudentGuardians(params)
export const createStudentGuardian = input => usesFamilySetup()
  ? http.post(`${root}/guardians`, input) : admin.createStudentGuardian(input)
export const deleteStudentGuardian = id => usesFamilySetup()
  ? http.delete(`${root}/guardians/${id}`) : admin.deleteStudentGuardian(id)
export const verifyStudentGuardian = (id, input) => usesFamilySetup()
  ? http.post(`${root}/guardians/${id}/verification`, input) : admin.verifyStudentGuardian(id, input)
export const restrictStudentGuardian = (id, input) => usesFamilySetup()
  ? http.post(`${root}/guardians/${id}/restriction`, input) : admin.restrictStudentGuardian(id, input)
export const consentStudentGuardian = (id, input) => usesFamilySetup()
  ? http.post(`${root}/guardians/${id}/consent`, input) : admin.consentStudentGuardian(id, input)
export const importFamilyRoster = (schoolId, rows) => http.post(`${root}/roster?${query({ schoolId })}`, rows)
export const listFamilyParentOptions = params => usesFamilySetup()
  ? http.get(`${root}/parent-options?${query(params)}`) : admin.listEducationParents(params)
export const saveFamilyHeadTeacher = input => http.put(`${root}/head-teacher`, input)
