import {Link, useLocation} from 'react-router-dom';
import './AdminNav.css';

const items = [
  {to: '/admin/users', label: '회원 관리'},
  {to: '/admin/stream-recovery', label: 'Stream 복구'},
];

export default function AdminNav() {
  const {pathname} = useLocation();
  return <nav className="admin-nav" aria-label="관리자 메뉴">
    {items.map(item => <Link key={item.to} className={pathname.startsWith(item.to) ? 'active' : ''} to={item.to}>{item.label}</Link>)}
  </nav>;
}
