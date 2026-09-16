-- V3 退运/销毁终态与税费清算：未缴税费在处置完成后作废（不可再缴纳）
ALTER TABLE tax_records ADD COLUMN void_reason VARCHAR(255);
