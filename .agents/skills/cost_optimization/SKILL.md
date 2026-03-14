---
name: AI Cost Optimization
description: Guidelines for AI agents to minimize token consumption and operational costs while working on the MediAgent codebase.
---
# AI Cost Optimization Skill

When acting as an AI developer or architect in this repository, follow these strategies to preserve the user's budget.

## 1. Context Minimization
- **Use `list_files` First**: Before reading a file, confirm its existence and location.
- **Selective `read_file`**:
    - Avoid reading entire large files.
    - Use `indentation` mode to extract specific classes or methods.
    - Use `offset` and `limit` for log files or long data files.
- **Leverage `environment_details`**: Use the provided workspace file list instead of running `ls -R`.

## 2. Tool Efficiency
- **Plan Ahead**: Combine multiple logic steps into a single response.
- **Avoid Redundant Calls**: If you have the file content in context, don't read it again.
- **Search vs. Read**: Use `search_files` to find specific code patterns instead of manually browsing multiple files.

## 3. Development Best Practices
- **Test Locally**: Suggest running `pytest` or `npm run dev` to verify changes before requesting complex remote deployments.
- **Dry Runs**: For infrastructure changes, review `template.yaml` thoroughly before suggesting modifications.
- **Incremental Edits**: Use the `edit` tool for precise string replacements rather than rewriting entire files with `write_to_file`.

## 4. Communication Efficiency
- **Be Concise**: Stay technical and direct. Avoid conversational filler.
- **Clear Todos**: Keep the task list updated to track progress and avoid repeating steps.
- **Specific Questions**: If you need info, ask specific questions with suggested answers.
