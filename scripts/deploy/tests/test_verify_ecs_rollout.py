import copy
import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location(
    'verify_ecs_rollout', Path(__file__).resolve().parents[1] / 'verify_ecs_rollout.py')
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)
EXPECTED = 'arn:aws:ecs:us-east-1:123456789012:task-definition/compira-qa-task:8'
BASE = {'services': [{'taskDefinition': EXPECTED, 'desiredCount': 1,
                     'runningCount': 1, 'pendingCount': 0,
                     'deployments': [{'taskDefinition': EXPECTED,
                                      'rolloutState': 'COMPLETED'}]}]}


class RolloutTests(unittest.TestCase):
    def wait(self, responses, attempts=3):
        iterator = iter(responses)
        self.delays = []
        return module.wait_for_rollout(
            EXPECTED, lambda: next(iterator), self.delays.append, attempts=attempts)

    def test_accepts_completed_revision(self):
        result = self.wait([copy.deepcopy(BASE)])
        self.assertEqual(result['taskDefinition'], EXPECTED)
        self.assertEqual(self.delays, [])

    def test_waits_for_eventually_consistent_rollout_confirmation(self):
        lag = copy.deepcopy(BASE)
        lag['services'][0]['deployments'][0]['rolloutState'] = 'IN_PROGRESS'
        self.wait([lag, copy.deepcopy(BASE)])
        self.assertEqual(self.delays, [5])

    def test_waits_for_old_deployment_to_disappear(self):
        lag = copy.deepcopy(BASE)
        lag['services'][0]['deployments'].append(
            {'taskDefinition': 'old', 'rolloutState': 'COMPLETED'})
        self.wait([lag, copy.deepcopy(BASE)])
        self.assertEqual(self.delays, [5])

    def test_rejects_rollback_without_retry(self):
        bad = copy.deepcopy(BASE)
        bad['services'][0]['taskDefinition'] = 'old'
        with self.assertRaisesRegex(RuntimeError, 'rolled back'):
            self.wait([bad])
        self.assertEqual(self.delays, [])

    def test_rejects_zero_desired_tasks(self):
        bad = copy.deepcopy(BASE)
        bad['services'][0]['desiredCount'] = 0
        with self.assertRaisesRegex(RuntimeError, 'zero'):
            self.wait([bad])

    def test_rejects_failed_rollout(self):
        bad = copy.deepcopy(BASE)
        bad['services'][0]['deployments'][0]['rolloutState'] = 'FAILED'
        with self.assertRaisesRegex(RuntimeError, 'failed'):
            self.wait([bad])

    def test_times_out_incomplete_rollout(self):
        bad = copy.deepcopy(BASE)
        bad['services'][0]['deployments'][0]['rolloutState'] = 'IN_PROGRESS'
        with self.assertRaisesRegex(RuntimeError, 'bounded'):
            self.wait([bad, bad, bad])
        self.assertEqual(self.delays, [5, 5])

    def test_waits_for_positive_running_count_and_no_pending_tasks(self):
        lag = copy.deepcopy(BASE)
        lag['services'][0].update(runningCount=0, pendingCount=1)
        self.wait([lag, copy.deepcopy(BASE)])
        self.assertEqual(self.delays, [5])

    def test_rejects_missing_or_inaccessible_service(self):
        for response in [{'services': []}, {'services': [], 'failures': [{'reason': 'MISSING'}]}]:
            with self.subTest(response=response):
                with self.assertRaisesRegex(RuntimeError, 'accessible'):
                    self.wait([response])


if __name__ == '__main__':
    unittest.main()
