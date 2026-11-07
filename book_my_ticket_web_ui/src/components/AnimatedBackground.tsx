/** Fixed, decorative backdrop: drifting aurora orbs over a receding 3D perspective grid. */
export function AnimatedBackground() {
  return (
    <div className="backdrop" aria-hidden>
      <div className="backdrop__orb backdrop__orb--a" />
      <div className="backdrop__orb backdrop__orb--b" />
      <div className="backdrop__orb backdrop__orb--c" />
      <div className="backdrop__grid" />
      <div className="backdrop__noise" />
    </div>
  );
}
