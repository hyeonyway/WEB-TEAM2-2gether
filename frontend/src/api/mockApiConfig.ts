const MOCK_API_STORAGE_KEY='USE_MOCK_API';

export function isMockApiEnabled():boolean{
  const environmentValue=import.meta.env.VITE_USE_MOCK_API;
  if(environmentValue!==undefined){
    return environmentValue.trim().toLowerCase()==='true';
  }

  return localStorage.getItem(MOCK_API_STORAGE_KEY)?.trim().toLowerCase()==='true';
}
