import { useQuery } from '@tanstack/react-query';
import { bookingApi, catalogApi, inventoryApi, queryKeys } from '../api';
import type { BookingStatus } from '../api/types';
import { useCity } from './city';

/** Upcoming events in the selected city (catalog-service caches this server-side, 30s Cache-Control). */
export function useCityEvents() {
  const { city } = useCity();
  return useQuery({
    queryKey: queryKeys.events(city),
    queryFn: ({ signal }) => catalogApi.events(city, 0, 100, signal),
    staleTime: 30_000,
    select: (page) => page.items,
  });
}

export function useEventDetails(id: string) {
  return useQuery({ queryKey: queryKeys.event(id), queryFn: ({ signal }) => catalogApi.event(id, signal), staleTime: 30_000 });
}

export function useShow(id: string) {
  return useQuery({ queryKey: queryKeys.show(id), queryFn: ({ signal }) => catalogApi.show(id, signal), staleTime: 30_000 });
}

/** Live seat map: polled so seats taken by other users disappear while you choose. */
export function useSeatMap(showId: string, pollMs = 10_000) {
  return useQuery({
    queryKey: queryKeys.seatMap(showId),
    queryFn: ({ signal }) => inventoryApi.seatMap(showId, signal),
    refetchInterval: pollMs,
    refetchIntervalInBackground: false,
  });
}

const POLLING_STATUSES: BookingStatus[] = ['PENDING_SEATS'];

export function useBooking(id: string, awaitingConfirmation: boolean) {
  return useQuery({
    queryKey: queryKeys.booking(id),
    queryFn: ({ signal }) => bookingApi.get(id, signal),
    refetchInterval: (query) => {
      const status = query.state.data?.status;
      if (!status) return false;
      if (POLLING_STATUSES.includes(status)) return 1000;
      if (awaitingConfirmation && status === 'PENDING_PAYMENT') return 1500;
      return false;
    },
  });
}

export function useMyBookings(page: number) {
  return useQuery({
    queryKey: queryKeys.myBookings(page),
    queryFn: ({ signal }) => bookingApi.mine(page, 12, signal),
    placeholderData: (previous) => previous,
  });
}
