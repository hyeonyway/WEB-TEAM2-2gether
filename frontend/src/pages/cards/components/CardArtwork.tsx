import type {CardTheme} from '../../../dto/auctionDto';

export default function CardArtwork({theme}:{theme:CardTheme}){
  return <div className={`mini-card ${theme}`}><i>HP 70</i><span>●</span><small>POKÉMON</small></div>;
}
