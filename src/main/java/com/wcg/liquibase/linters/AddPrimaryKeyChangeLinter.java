package com.wcg.liquibase.linters;

import com.wcg.liquibase.Linter;
import com.wcg.liquibase.config.rules.RuleRunner;
import com.wcg.liquibase.config.rules.RuleType;
import liquibase.change.core.AddPrimaryKeyChange;
import liquibase.exception.ChangeLogParseException;

public class AddPrimaryKeyChangeLinter implements Linter<AddPrimaryKeyChange> {

    private static final ObjectNameLinter objectNameLinter = new ObjectNameLinter();

    @Override
    public void lint(AddPrimaryKeyChange change, RuleRunner ruleRunner) throws ChangeLogParseException {
        getObjectNameLinter().lintObjectNameLength(change.getConstraintName(), change, ruleRunner);
        ruleRunner.forChange(change)
                .run(RuleType.PRIMARY_KEY_MUST_BE_NAMED, change.getConstraintName())
                .run(RuleType.PRIMARY_KEY_MUST_USE_TABLE_NAME, change.getConstraintName());
    }

    ObjectNameLinter getObjectNameLinter() {
        return objectNameLinter;
    }

}
