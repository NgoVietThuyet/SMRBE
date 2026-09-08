// Lớp entity cha dùng chung cho các entity khác
using System;

namespace BE.Core.Entities
{
    public abstract class BaseEntity
    {
        public string CreateBy { get; set; }
        public DateTime? CreateDate { get; set; }
        public string UpdateBy { get; set; }
        public DateTime? UpdateDate { get; set; }
    }
}
