INSERT INTO problems (title, description, test_cases)
VALUES ('Two Sum',
        'Given an array of integers nums and an integer target, return indices of the two numbers such that they add up to target.',
        '[
          {"input": [[2, 7, 11, 15], 9], "expectedOutput": [0, 1], "hidden": false},
          {"input": [[3, 2, 4], 6],      "expectedOutput": [1, 2], "hidden": false},
          {"input": [[3, 3], 6],         "expectedOutput": [0, 1], "hidden": true}
        ]');

INSERT INTO problem_detail (problem_id, language, code_template)
VALUES (1, 'PYTHON',
'import json, sys

def two_sum(nums, target):
    # Write your solution here
    pass

data = json.loads(input())
result = two_sum(data[0], data[1])
print(json.dumps(result))');

INSERT INTO problem_detail (problem_id, language, code_template)
VALUES (1, 'JAVA',
'import java.util.*;

public class Solution {
    public int[] twoSum(int[] nums, int target) {
        // Write your solution here
        return new int[]{};
    }

    public static void main(String[] args) throws Exception {
        // boilerplate to parse input and print output
    }
}');

INSERT INTO problem_detail (problem_id, language, code_template)
VALUES (1, 'CPP',
'#include <bits/stdc++.h>
using namespace std;

vector<int> twoSum(vector<int>& nums, int target) {
    // Write your solution here
    return {};
}

int main() {
    // boilerplate to parse input and print output
}');
