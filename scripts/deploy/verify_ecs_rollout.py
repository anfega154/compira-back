#!/usr/bin/env python3
"""Verify the requested ECS revision, allowing bounded control-plane lag."""
import argparse
import json
import subprocess
import time


def wait_for_rollout(expected, describe, pause=time.sleep, attempts=24, delay=5):
    for attempt in range(attempts):
        response = describe()
        services = response.get('services', [])
        if response.get('failures') or len(services) != 1:
            raise RuntimeError('Expected one accessible ECS service')
        service = services[0]
        if service.get('taskDefinition') != expected:
            raise RuntimeError('Deployment rolled back or was replaced by another task')
        if service.get('desiredCount', 0) <= 0:
            raise RuntimeError('No healthy running tasks: desired count is zero')
        deployments = service.get('deployments', [])
        requested = [d for d in deployments if d.get('taskDefinition') == expected]
        if any(d.get('rolloutState') == 'FAILED' for d in requested):
            raise RuntimeError('Requested rollout failed')
        if (service.get('runningCount') == service['desiredCount']
                and service.get('pendingCount', 0) == 0
                and len(deployments) == 1 and len(requested) == 1
                and requested[0].get('rolloutState') == 'COMPLETED'):
            return service
        if attempt + 1 < attempts:
            pause(delay)
    raise RuntimeError('Rollout incomplete after bounded confirmation wait')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--cluster', required=True)
    parser.add_argument('--service', required=True)
    parser.add_argument('--task-definition', required=True)
    args = parser.parse_args()

    def describe():
        result = subprocess.run(
            ['aws', 'ecs', 'describe-services', '--cluster', args.cluster,
             '--services', args.service, '--output', 'json'],
            check=True, capture_output=True, text=True)
        return json.loads(result.stdout)

    service = wait_for_rollout(args.task_definition, describe)
    print(f"Confirmed task {service['taskDefinition']}; "
          f"running {service['runningCount']}/{service['desiredCount']}")


if __name__ == '__main__':
    main()
