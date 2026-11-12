package com.vitor.remotecolonymanager;

import com.minecolonies.api.colony.workorders.WorkOrderType;

public final class WorkOrderTypeNames {
   private WorkOrderTypeNames() {
   }

   public static String getName(WorkOrderType type) {
      if (type == null) {
         return "Desconhecido";
      } else {
         switch (type) {
            case BUILD:
               return "Construção";
            case UPGRADE:
               return "Melhoria";
            case REPAIR:
               return "Reparo";
            case REMOVE:
               return "Remoção";
            default:
               return "Desconhecido";
         }
      }
   }
}
