import { motion } from 'framer-motion';
import { Link } from 'react-router-dom';

export function NotFoundPage() {
  return (
    <div className="state">
      <motion.div
        className="not-found__code"
        animate={{ rotateY: [0, 18, -18, 0], rotateX: [0, -10, 10, 0] }}
        transition={{ duration: 6, repeat: Infinity, ease: 'easeInOut' }}
      >
        404
      </motion.div>
      <h1>This show isn't on the schedule</h1>
      <p>The page you're looking for doesn't exist or has moved.</p>
      <Link to="/" className="btn btn--primary">Back to home</Link>
    </div>
  );
}
