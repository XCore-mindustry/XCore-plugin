/**
 * Public Data Transfer Objects and query interfaces for XCore Stats.
 *
 * CRITICAL PRIVACY INVARIANT:
 * Public DTOs MUST NEVER include private/internal credentials or identifiers:
 * - NO player UUIDs (server-internal identifier only)
 * - NO Discord snowflakes / discord_id
 * - NO IP addresses (last_ip)
 * - NO password hashes or auth tokens
 * - NO device tokens or device token hashes
 * - NO arbitrary unvalidated raw engine docs
 *
 * PID (Player ID integer) is the sole public player identity.
 */

export type PaginatedResult<T> = {
  items: T[];
  page: number;
  pageSize: number;
  total: number;
  totalPages: number;
};

export type PlayerSearchResult = {
  pid: number;
  nickname: string;
  username: string | null;
  totalPlayTime: number; // in minutes
  activeBadge: string | null;
  isAdmin?: boolean;
  online?: boolean;
  createdAt: string | null;
};

export type ModeStatsSummaryDTO = {
  gamesPlayed: number;
  gamesWon: number;
  bestWave: number;
  averageWave: number;
  bestPlacement: number;
  top3Finishes: number;
};

export type PlayerAggregateOverview = {
  overall: {
    gamesPlayed: number;
    gamesWon: number;
    blocksBuilt: number;
    blocksDeconstructed: number;
    blocksDestroyed: number;
    unitsProduced: number;
    unitsDestroyed: number;
  };
  pvp: ModeStatsSummaryDTO;
  survival: ModeStatsSummaryDTO;
  hexed: ModeStatsSummaryDTO;
};

export type LadderStandingDTO = {
  ladder: string;
  seasonNumber: number;
  seasonName: string;
  rating: number;
  peakRating: number;
  matches: number;
  wins: number;
  /** Top-3 finishes on a free-for-all ladder (HexedCore); null on a ladder that does not count them. */
  top3: number | null;
  rank: number;
  participants: number;
};

export type PlayerProfile = {
  pid: number;
  nickname: string;
  username: string | null;
  totalPlayTime: number; // in minutes
  activeBadge: string | null;
  unlockedBadges: string[];
  description: string | null;
  isAdmin?: boolean;
  online: boolean;
  onlineServer: string | null;
  createdAt: string | null;
  standings: LadderStandingDTO[];
  overview: PlayerAggregateOverview;
};

export type LeaderboardCategory = "playtime" | "pvp" | "hexed" | "hexed-elo";

export type LeaderboardEntry = {
  rank: number;
  pid: number | null;
  nickname: string;
  username: string | null;
  activeBadge: string | null;
  value: number; // minutes for playtime, rating points for pvp/hexed
  formattedValue: string;
  matches?: number;
  wins?: number;
  season?: number;
};

export type LeaderboardResult = PaginatedResult<LeaderboardEntry> & {
  category: LeaderboardCategory;
  season?: number;
  ladder?: string;
};

export type MatchFilters = {
  pid?: number;
  ladder?: string;
  category?: string; // stats_category from games_v2 e.g. "PVP", "SURVIVAL", "HEXED"
  season?: number;
  page?: number;
  pageSize?: number;
};

export type PublicMatchSummary = {
  matchId: string;
  ladder: string | null;
  season: number | null;
  startedAt: string | null;
  endedAt: string | null;
  durationSeconds: number | null;
  map: string | null;
  gameMode: string | null;
  category: string | null;
  ranked: boolean;
  playerCount: number;
  finishReason: string | null;
  wavesReached: number | null;
  playerOutcome?: {
    win: boolean;
    placement: number | null;
    ratingDelta: number | null;
    ratingAfter: number | null;
  };
};

export type PublicMatchParticipant = {
  pid: number | null;
  name: string;
  username: string | null;
  activeBadge: string | null;
  team: number | null;
  placement: number | null;
  win: boolean;
  ratingBefore: number | null;
  delta: number | null;
  ratingAfter: number | null;
  counted: boolean | null;
  reason: string | null;
  blocksBuilt: number | null;
  blocksDeconstructed: number | null;
  blocksDestroyed: number | null;
  unitsProduced: number | null;
  unitsDestroyed: number | null;
};

export type PublicMatchDetail = {
  matchId: string;
  ladder: string | null;
  season: number | null;
  startedAt: string | null;
  endedAt: string | null;
  durationSeconds: number | null;
  map: string | null;
  serverName: string | null;
  gameMode: string | null;
  category: string | null;
  ranked: boolean;
  countedInStats: boolean;
  finishReason: string | null;
  wavesReached: number | null;
  playerCount: number;
  teamSizes: Record<string, number> | null;
  participants: PublicMatchParticipant[];
};

export type LinkedProfile = {
  pid: number;
  nickname: string;
  username: string | null;
  totalPlayTime: number;
  activeBadge: string | null;
  online: boolean;
  createdAt: string | null;
};
