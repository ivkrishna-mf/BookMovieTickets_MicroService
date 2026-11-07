import { useMotionValue, useReducedMotion, useSpring, useTransform, type MotionValue } from 'framer-motion';
import { useCallback, type PointerEvent } from 'react';

export interface Tilt {
  rotateX: MotionValue<number>;
  rotateY: MotionValue<number>;
  glareX: MotionValue<string>;
  glareY: MotionValue<string>;
  onPointerMove: (e: PointerEvent<HTMLElement>) => void;
  onPointerLeave: () => void;
}

/** Pointer-driven 3D tilt with spring physics and a moving glare highlight. Disabled for reduced motion. */
export function useTilt(maxDegrees = 12): Tilt {
  const reduce = useReducedMotion();
  const px = useMotionValue(0.5);
  const py = useMotionValue(0.5);
  const spring = { stiffness: 220, damping: 20, mass: 0.6 };
  const rotateY = useSpring(useTransform(px, [0, 1], [-maxDegrees, maxDegrees]), spring);
  const rotateX = useSpring(useTransform(py, [0, 1], [maxDegrees, -maxDegrees]), spring);
  const glareX = useTransform(px, (v) => `${v * 100}%`);
  const glareY = useTransform(py, (v) => `${v * 100}%`);

  const onPointerMove = useCallback(
    (e: PointerEvent<HTMLElement>) => {
      if (reduce || e.pointerType === 'touch') return;
      const rect = e.currentTarget.getBoundingClientRect();
      px.set((e.clientX - rect.left) / rect.width);
      py.set((e.clientY - rect.top) / rect.height);
    },
    [px, py, reduce],
  );

  const onPointerLeave = useCallback(() => {
    px.set(0.5);
    py.set(0.5);
  }, [px, py]);

  return { rotateX, rotateY, glareX, glareY, onPointerMove, onPointerLeave };
}
