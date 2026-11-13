// Mirrors the Java records exposed by the BookMyTickets services through the API gateway.

export interface EventSummary {
  id: string;
  title: string;
  category: string;
  language: string | null;
  durationMinutes: number;
}

export interface EventPage {
  items: EventSummary[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface ShowSummary {
  id: string;
  venueId: string;
  venueName: string;
  city: string;
  startTime: string;
  status: string;
}

export interface EventDetails {
  id: string;
  title: string;
  description: string | null;
  category: string;
  language: string | null;
  durationMinutes: number;
  upcomingShows: ShowSummary[];
}

export interface ShowResponse {
  id: string;
  eventId: string;
  eventTitle: string;
  venueId: string;
  venueName: string;
  city: string;
  startTime: string;
  status: string;
  currency: string;
  maxSeatsPerBooking: number;
  priceByCategory: Record<string, number>;
}

export type SeatStatus = 'AVAILABLE' | 'HELD' | 'BOOKED';

export interface SeatView {
  seatNumber: string;
  row: string;
  index: number;
  category: string;
  status: SeatStatus;
}

export interface SeatMapResponse {
  showId: string;
  available: number;
  seats: SeatView[];
}

export type BookingStatus = 'PENDING_SEATS' | 'PENDING_PAYMENT' | 'CONFIRMED' | 'CANCELLED' | 'EXPIRED' | 'FAILED';

export interface BookingResponse {
  id: string;
  status: BookingStatus;
  showId: string;
  eventTitle: string;
  venueName: string;
  showTime: string;
  seats: string[];
  totalAmount: number;
  currency: string;
  paymentClientSecret: string | null;
  expiresAt: string | null;
  ticketCode: string | null;
  failureReason: string | null;
  createdAt: string;
}

/**
 * Spring Data page as returned by booking-service. It uses PageSerializationMode.VIA_DTO:
 * { content, page: { size, number, totalElements, totalPages } }. The legacy flat PageImpl shape is also accepted.
 */
export interface SpringPageDto<T> {
  content: T[];
  page?: { size: number; number: number; totalElements: number; totalPages: number };
  number?: number;
  size?: number;
  totalElements?: number;
  totalPages?: number;
}

/** Normalised page used by the UI. */
export interface Page<T> {
  content: T[];
  number: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

export interface CreateVenueRequest {
  name: string;
  city: string;
  address?: string;
  capacity: number;
}

export interface CreateEventRequest {
  title: string;
  description?: string;
  category: string;
  language?: string;
  durationMinutes: number;
}

export interface SeatSection {
  category: string;
  rows: string[];
  seatsPerRow: number;
}

export interface CreateShowRequest {
  eventId: string;
  venueId: string;
  startTime: string;
  currency: string;
  maxSeatsPerBooking: number;
  priceByCategory: Record<string, number>;
  sections: SeatSection[];
}

export interface IdResponse {
  id: string;
}
