const MOCK_API_STORAGE_KEY='USE_MOCK_API';

export function isMockApiEnabled():boolean{
  return localStorage.getItem(MOCK_API_STORAGE_KEY)!=='false';
}
